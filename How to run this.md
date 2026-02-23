looks good # How to Run APIDoc Token Optimizer

## Prerequisites

- **Java 21** or later — verify with `java -version`
- No additional software required; Maven is bundled via the wrapper

---

## 1. Clone or open the project

If you haven't already, clone the repository and navigate into it:

```bash
git clone <repo-url>
cd APIDoc-token-optimizer
```

---

## 2. Start the application

Run the included Maven wrapper — no separate Maven installation needed:

**macOS / Linux:**
```bash
./mvnw spring-boot:run
```

**Windows:**
```cmd
mvnw.cmd spring-boot:run
```

Wait for a line like this in the console output:

```
Started UtilApplication in 2.x seconds
```

The app is now running at **http://localhost:10001**

---

## 3. Open the UI

Open your browser and go to:

```
http://localhost:10001
```

You should see the **Postman → Swagger / OpenAPI Converter** landing page.

---

## 4. Feed the example Postman collection

An example collection is included in the `Examples/` folder:

```
Examples/Postman Echo.postman_collection.json
```

### Option A — Upload file (recommended for the example)

1. Click the **Upload File** tab
2. Click **Choose File**
3. Select `Examples/Postman Echo.postman_collection.json`
4. Click **Convert**

![Upload the sample collection](Examples/Upload%20the%20Sample%20postmancollection.png)

### Option B — Paste JSON

1. Open `Examples/Postman Echo.postman_collection.json` in any text editor
2. Copy the entire contents
3. Paste into the **Paste JSON** textarea
4. Click **Convert**

---

## 5. Check the expected response

After clicking **Convert**, scroll down to see the results panel.

For the **Postman Echo** example collection you should see results similar to:

| Metric | Value |
|---|---|
| Postman JSON tokens | **39,192** |
| OpenAPI YAML tokens | **11,751** |
| Token difference | **27,441** |
| Savings | **Swagger saves ~70%** |

![Expected response](Examples/Expected%20response.png)

The page also shows:
- A **token reduction bar** visualising the savings
- Side-by-side panels with the original Postman JSON and the generated OpenAPI 3.0 YAML
- A **Copy** button on each panel to grab the output

---

## 6. Use the output

Click **Copy** on the **OpenAPI 3.0 YAML** panel and paste it directly into your AI assistant's context window. The YAML is roughly **70% smaller** than the raw Postman collection, which means significantly lower token cost when feeding API documentation to Claude or other LLMs.

---

## Stopping the app

Press `Ctrl + C` in the terminal where the app is running.