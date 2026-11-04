const $ = (id) => document.getElementById(id);
let csrf,
  signup = false,
  products = [],
  movements = [],
  activeProduct;
const money = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    value,
  );
function notice(text, error = false) {
  $("notice").textContent = text;
  $("notice").className = error ? "error" : "";
  $("notice").hidden = false;
}
async function token() {
  const response = await fetch("/api/auth/csrf");
  if (!response.ok) throw new Error("Unable to initialize secure session.");
  csrf = await response.json();
}
async function api(url, method = "GET", data) {
  if (method !== "GET" && !csrf) await token();
  const response = await fetch(url, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(method !== "GET" ? { [csrf.headerName]: csrf.token } : {}),
    },
    ...(data === undefined ? {} : { body: JSON.stringify(data) }),
  });
  const result = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    if (response.status === 401) showAuth();
    throw new Error(result?.error || "Request failed.");
  }
  return result;
}
async function busy(button, run) {
  button.disabled = true;
  try {
    await run();
  } catch (e) {
    notice(e.message, true);
  } finally {
    button.disabled = false;
  }
}
function showAuth() {
  products = [];
  movements = [];
  $("products").replaceChildren();
  $("movements").replaceChildren();
  $("metrics").replaceChildren();
  $("auth").hidden = false;
  $("dashboard").hidden = true;
  $("logout").hidden = true;
  for (const dialog of document.querySelectorAll("dialog[open]"))
    dialog.close();
}
function setMode(value) {
  signup = value;
  $("nameLabel").hidden = !value;
  $("name").required = value;
  $("authTitle").textContent = value ? "Create your workspace" : "Welcome back";
  $("authSubmit").textContent = value ? "Create account →" : "Sign in →";
  $("password").autocomplete = value ? "new-password" : "current-password";
  $("loginTab").classList.toggle("selected", !value);
  $("signupTab").classList.toggle("selected", value);
}
function td(text) {
  const cell = document.createElement("td");
  cell.textContent = text;
  return cell;
}
async function refresh() {
  const user = await api("/api/auth/me");
  [products, movements] = await Promise.all([
    api("/api/products"),
    api("/api/movements"),
  ]);
  $("workspace").textContent = user.name.toUpperCase() + " / WORKSPACE";
  $("auth").hidden = true;
  $("dashboard").hidden = false;
  $("logout").hidden = false;
  render();
}
function render() {
  const total = products.reduce((s, p) => s + p.quantity, 0),
    value = products.reduce((s, p) => s + p.quantity * Number(p.price), 0),
    low = products.filter((p) => p.quantity <= p.reorderLevel).length;
  $("metrics").replaceChildren(
    ...[
      ["Products", products.length],
      ["Units on hand", total.toLocaleString()],
      ["Inventory value", money(value)],
      ["Needs attention", low],
    ].map(([label, value]) => {
      const el = document.createElement("div");
      el.className = "wallet";
      const name = document.createElement("span");
      name.textContent = label;
      const number = document.createElement("strong");
      number.textContent = value;
      el.append(name, number);
      return el;
    }),
  );
  const query = $("search").value.toLowerCase(),
    filter = $("filter").value;
  const visible = products.filter(
    (p) =>
      `${p.name} ${p.sku} ${p.category}`.toLowerCase().includes(query) &&
      (filter === "all" ||
        (filter === "low" ? p.quantity <= p.reorderLevel : p.quantity === 0)),
  );
  $("products").replaceChildren(
    ...visible.map((p) => {
      const row = document.createElement("tr"),
        title = td(p.name),
        sku = document.createElement("small");
      sku.textContent = p.sku;
      title.append(sku);
      const status = td(""),
        badge = document.createElement("span");
      badge.className =
        "tag " +
        (p.quantity === 0
          ? "out"
          : p.quantity <= p.reorderLevel
            ? "low"
            : "ok");
      badge.textContent =
        p.quantity === 0
          ? "Out of stock"
          : p.quantity <= p.reorderLevel
            ? "Low stock"
            : "In stock";
      status.append(badge);
      const actions = td(""),
        wrap = document.createElement("div");
      wrap.className = "actions";
      for (const [text, handler] of [
        ["Edit", () => edit(p)],
        ["Adjust", () => adjust(p)],
        ["Delete", () => remove(p)],
      ]) {
        const button = document.createElement("button");
        button.textContent = text;
        button.className = "quiet";
        button.onclick = handler;
        wrap.append(button);
      }
      actions.append(wrap);
      row.append(
        title,
        td(p.category),
        td(p.quantity),
        td(money(p.price)),
        status,
        actions,
      );
      return row;
    }),
  );
  $("empty").hidden = visible.length > 0;
  $("empty").querySelector("h2").textContent = products.length
    ? "No matching products."
    : "A fresh start for your stock.";
  $("movements").replaceChildren(
    ...movements.map((m) => {
      const row = document.createElement("tr");
      row.append(
        td(new Date(m.createdAt).toLocaleString()),
        td(m.productName),
        td(m.reason),
        td((m.delta > 0 ? "+" : "") + m.delta),
        td(m.resultingQuantity),
      );
      return row;
    }),
  );
  if (!movements.length) {
    const row = document.createElement("tr"),
      cell = td("No stock movements yet.");
    cell.colSpan = 5;
    row.append(cell);
    $("movements").append(row);
  }
}
function edit(p) {
  activeProduct = p;
  $("productForm").reset();
  $("productTitle").textContent = p ? "Edit product" : "Add product";
  for (const [id, key] of [
    ["sku", "sku"],
    ["productName", "name"],
    ["category", "category"],
    ["price", "price"],
    ["quantity", "quantity"],
    ["reorderLevel", "reorderLevel"],
  ])
    if (p) $(id).value = p[key];
  $("quantity").disabled = !!p;
  $("productDialog").showModal();
}
function adjust(p) {
  activeProduct = p;
  $("adjustForm").reset();
  $("adjustProduct").textContent = `${p.name} · ${p.quantity} units on hand`;
  $("adjustDialog").showModal();
}
function remove(p) {
  activeProduct = p;
  $("deleteProduct").textContent = p.name;
  $("deleteDialog").showModal();
}
$("loginTab").onclick = () => setMode(false);
$("signupTab").onclick = () => setMode(true);
$("authForm").onsubmit = (e) => {
  e.preventDefault();
  busy($("authSubmit"), async () => {
    const data = { email: $("email").value, password: $("password").value };
    if (signup)
      await api("/api/auth/signup", "POST", { ...data, name: $("name").value });
    await api("/api/auth/login", "POST", data);
    $("password").value = "";
    await token();
    $("notice").hidden = true;
    await refresh();
  });
};
$("logout").onclick = () =>
  busy($("logout"), async () => {
    await api("/api/auth/logout", "POST", {});
    csrf = null;
    showAuth();
    await token();
  });
$("add").onclick = () => edit(null);
$("refresh").onclick = () => busy($("refresh"), refresh);
$("search").oninput = render;
$("filter").onchange = render;
for (const button of document.querySelectorAll("[data-close]"))
  button.onclick = () => $(button.dataset.close).close();
$("productForm").onsubmit = (e) => {
  e.preventDefault();
  busy(e.currentTarget.querySelector(".primary"), async () => {
    const body = {
      sku: $("sku").value,
      name: $("productName").value,
      category: $("category").value,
      price: $("price").value,
      quantity: Number($("quantity").value),
      reorderLevel: Number($("reorderLevel").value),
      version: activeProduct?.version ?? null,
    };
    await api(
      "/api/products" + (activeProduct ? "/" + activeProduct.id : ""),
      activeProduct ? "PUT" : "POST",
      body,
    );
    $("productDialog").close();
    await refresh();
    notice("Product saved.");
  });
};
$("adjustForm").onsubmit = (e) => {
  e.preventDefault();
  busy(e.currentTarget.querySelector(".primary"), async () => {
    await api(`/api/products/${activeProduct.id}/adjustments`, "POST", {
      delta: Number($("delta").value),
      reason: $("reason").value,
      version: activeProduct.version,
    });
    $("adjustDialog").close();
    await refresh();
    notice("Stock adjustment recorded.");
  });
};
$("confirmDelete").onclick = () =>
  busy($("confirmDelete"), async () => {
    await api(
      `/api/products/${activeProduct.id}?version=${activeProduct.version}`,
      "DELETE",
    );
    $("deleteDialog").close();
    await refresh();
    notice("Product deleted.");
  });
$("export").onclick = () => {
  const cell = (x) =>
    '"' +
    String(x)
      .replace(/"/g, '""')
      .replace(/^[=+@\-]/, "'$&") +
    '"';
  const rows = [
    [
      "SKU",
      "Name",
      "Category",
      "Quantity",
      "Reorder level",
      "Unit price (INR)",
    ],
    ...products.map((p) => [
      p.sku,
      p.name,
      p.category,
      p.quantity,
      p.reorderLevel,
      p.price,
    ]),
  ];
  const url = URL.createObjectURL(
    new Blob([rows.map((r) => r.map(cell).join(",")).join("\r\n")], {
      type: "text/csv",
    }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "inventory-products.csv";
  a.click();
  URL.revokeObjectURL(url);
};
$("year").textContent = new Date().getFullYear();
token()
  .then(refresh)
  .catch((e) => {
    showAuth();
    if (!/sign in/i.test(e.message)) notice(e.message, true);
  });
