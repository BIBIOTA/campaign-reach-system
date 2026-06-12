// Post-processes the openapi-to-postmanv2 output so the PUBLISHED Postman collection
// is callable right after import — no manual variable entry needed (see docs.yml pipeline).
//
// openapi-to-postmanv2 emits Basic-auth credentials as the {{basicAuthUsername}} /
// {{basicAuthPassword}} variables but leaves them undefined, and derives {{baseUrl}} from the
// spec's server. We inject ready-to-call defaults that match the local dev stack documented in
// `.env.example` (operator / operator-pass on http://localhost:8080), so importing the collection
// and hitting Send against a locally-running app just works. Override the variables in Postman for
// any other environment.
//
// Usage: node docs/scripts/finalize-postman-collection.mjs <collection.json>
import { readFileSync, writeFileSync } from "node:fs";

const path = process.argv[2];
if (!path) {
  console.error("usage: node finalize-postman-collection.mjs <collection.json>");
  process.exit(1);
}

// Must match `.env.example` (OPERATOR_USERNAME / OPERATOR_PASSWORD) and the server URL declared in
// OpenApiConfig, so the in-repo defaults stay aligned with the local dev stack.
const DEFAULTS = {
  baseUrl: "http://localhost:8080",
  basicAuthUsername: "operator",
  basicAuthPassword: "operator-pass",
};

const collection = JSON.parse(readFileSync(path, "utf8"));
collection.variable = Array.isArray(collection.variable) ? collection.variable : [];

for (const [key, value] of Object.entries(DEFAULTS)) {
  const existing = collection.variable.find((v) => v.key === key);
  if (existing) {
    existing.value = value;
    existing.type = "string";
  } else {
    collection.variable.push({ key, value, type: "string" });
  }
}

writeFileSync(path, `${JSON.stringify(collection, null, 2)}\n`);
console.log(`Injected ready-to-call defaults: ${Object.keys(DEFAULTS).join(", ")}`);
