# Running GCM with server + remote client (JARs)

## Prerequisites

- **Java 17+** installed on **both** machines (`java -version`).
- **MySQL** running on the **server machine** (or reachable from it). Configure `server/DBConnector.java` (URL, user, password) or environment expectations to match your database.
- **Same LAN** or VPN unless you forward ports on your router (not covered here).

### JavaFX / platform note

This project’s `pom.xml` includes JavaFX with the **Windows** classifier. The shaded `GCM-Client.jar` you build on Windows is intended for **Windows clients**.  
If the remote PC is **Linux or macOS**, build the project on that OS (or adjust the `javafx-graphics` classifier in `pom.xml` for that platform) and use the new `GCM-Client.jar`.

---

## 1. Build the JARs (once, on your dev machine)

From the project root:

```bash
mvn clean package -DskipTests
```

Outputs (in the `target` folder):

| File           | Role                          |
|----------------|-------------------------------|
| `GCM-Server.jar` | Runs the socket server + DB |
| `GCM-Client.jar` | JavaFX desktop client       |

Copy **`GCM-Server.jar`** to the machine that will host the server.  
Copy **`GCM-Client.jar`** to each machine that will run the client.

---

## 2. Server machine (this device)

1. Ensure MySQL is up and the schema/data match what the app expects.
2. **Firewall:** allow **inbound TCP** on port **5555** (or whatever port you use).
   - Windows: *Windows Defender Firewall → Inbound Rules → New Rule → Port TCP 5555*.
3. Find this PC’s LAN IP, e.g. `ipconfig` → IPv4 like `192.168.1.50`.
4. Start the server:

```bash
java -jar GCM-Server.jar
```

Optional custom port:

```bash
java -jar GCM-Server.jar 5555
```

You should see logs indicating the server is listening. The server binds to **all interfaces** (`0.0.0.0`), so other devices on the network can connect to this machine’s IP.

---

## 3. Client machine (another device)

Run the client, passing the **server’s IP or hostname** as the **first** argument. Port **5555** is used by default; pass a second argument to override.

```bash
java -jar GCM-Client.jar 192.168.1.50
```

```bash
java -jar GCM-Client.jar 192.168.1.50 5555
```

Alternative (no positional args), using JVM system properties:

```bash
java -Dgcm.server.host=192.168.1.50 -Dgcm.server.port=5555 -jar GCM-Client.jar
```

Command-line host/port override system properties when you pass them.

If login shows “Failed to connect to server”, check: server is running, correct IP, firewall, and that both sides use the same port.

---

## 4. Quick checklist

| Check | |
|--------|--|
| Server `java` process running | |
| MySQL reachable from server | |
| Client JAR launched with **server LAN IP** | |
| Firewall allows TCP **5555** (or chosen port) on server | |
| Java 17+ on both hosts | |
| Client OS matches JavaFX build (Windows JAR → Windows client) | |
