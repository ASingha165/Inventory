package com.inventory.inventorys;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:inventory-test;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc
class InventorysApplicationTests {
  @Autowired MockMvc mvc;

  private String product(String sku, int qty, Long version) {
    return "{\"sku\":\""
        + sku
        + "\",\"name\":\"Notebook\",\"category\":\"Office\",\"quantity\":"
        + qty
        + ",\"reorderLevel\":5,\"price\":\"24.50\",\"version\":"
        + version
        + "}";
  }

  private MockHttpSession login(String email) throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Test Owner\",\"email\":\""
                        + email
                        + "\",\"password\":\"long-test-password\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"long-test-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }

  @Test
  void authAndCsrf() throws Exception {
    mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    var session = login("auth@example.com");
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("auth@example.com"));
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"auth@example.com\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/products")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(product("CSRF", 1, null)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    assertTrue(session.isInvalid());
  }

  @Test
  void ownerIsolationAndStockLedger() throws Exception {
    var owner = login("owner@example.com");
    var other = login("other@example.com");
    var created =
        mvc.perform(
                post("/api/products")
                    .session(owner)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(product("BOOK-1", 10, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.quantity").value(10))
            .andExpect(jsonPath("$.ownerEmail").doesNotExist())
            .andReturn();
    // Read generated id without tying the test to database sequence values.
    String json = created.getResponse().getContentAsString();
    long id = Long.parseLong(json.replaceFirst(".*\"id\":([0-9]+).*", "$1"));
    mvc.perform(get("/api/products/" + id).session(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/products").session(other)).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            post("/api/products/" + id + "/adjustments")
                .session(other)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":1,\"reason\":\"unauthorized\",\"version\":0}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/products/" + id + "/adjustments")
                .session(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-11,\"reason\":\"sale\",\"version\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/products/" + id + "/adjustments")
                .session(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-3,\"reason\":\"sale\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(7));
    mvc.perform(
            post("/api/products/" + id + "/adjustments")
                .session(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-3,\"reason\":\"duplicate\",\"version\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/products/" + id + "?version=1").session(owner).with(csrf()))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/products/" + id)
                .session(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product("BOOK-1", 7, 1L)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/products/" + id).session(owner)).andExpect(status().isOk());
    mvc.perform(
            post("/api/products/" + id + "/adjustments")
                .session(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":-7,\"reason\":\"clearance\",\"version\":1}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/products/" + id + "?version=2").session(owner).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/movements").session(owner)).andExpect(jsonPath("$.length()").value(4));
    mvc.perform(get("/api/movements").session(other)).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void validationAndDuplicates() throws Exception {
    var session = login("validate@example.com");
    mvc.perform(
            post("/api/products")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product("NEGATIVE", -1, null)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/products")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product("DUPLICATE", 0, null)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/products")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(product("duplicate", 0, null)))
        .andExpect(status().isConflict());
  }
}
