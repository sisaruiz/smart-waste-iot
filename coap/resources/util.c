/* util.c */

#include "util.h"
#include "json-util.h"
#include <string.h>
#include <stdlib.h>
#include "sys/log.h"

#define LOG_MODULE "util"
#define LOG_LEVEL  LOG_LEVEL_DBG

bool
get_json_field(const char *json, const char *key, char *out, size_t out_len) {
  struct json_util_state st;
  char value_buf[32];

  /* Initialize parser over the JSON text */
  json_util_init(&st, json, strlen(json));

  /* Iterate through each key/value pair */
  while(json_util_next(&st, value_buf, sizeof(value_buf))) {
    if(strcmp(st.key, key) == 0) {
      /* Copy value into caller’s buffer, NUL‑terminate */
      strncpy(out, st.value, out_len - 1);
      out[out_len - 1] = '\0';
      LOG_DBG("Parsed JSON field '%s' = '%s'\n", key, out);
      return true;
    }
  }

  /* Not found */
  LOG_WARN("JSON key '%s' not found in '%s'\n", key, json);
  return false;
}
