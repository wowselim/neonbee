# WatchVerticle

## Verticle Configuration

- The config file must be placed in the working directory inside a directory called `config` and the filename must be matching the full qualified class name of the verticle, i.e. **`config/io.neonbee.internal.verticle.WatchVerticle.yaml`**.
- The top-level `config` key is mandatory.

You can use the following options to configure the `WatchVerticle`.

| Property     | Type   | Required | Description                                                                                                                 | Default |
|--------------|--------|:--------:|-----------------------------------------------------------------------------------------------------------------------------|---------|
| `watchLogic` | string |    No    | Whether to listen only on `CREATE`, or also on `MODIFY` events. If both should be enabled, the value must be set to `copy`. | `~`     |

**Default Configuration of `watchLogic`:**

```yaml
---
config:
  watchLogic: ~
```
