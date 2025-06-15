/* util.h */

#ifndef UTIL_H_
#define UTIL_H_

#include <stdbool.h>
#include <stddef.h>

/**
 * Parse a flat JSON object and extract the string value for `key`.
 *
 * @param json      Null‑terminated JSON string (e.g. "{\"action\":\"true\",\"threshold\":\"1\"}")
 * @param key       Name of the field to extract
 * @param out       Buffer to receive the field’s value (as a C‑string)
 * @param out_len   Length of `out`; result is always NUL‑terminated
 * @return          true if `key` was found and copied; false otherwise
 */
bool get_json_field(const char *json, const char *key, char *out, size_t out_len);

#endif /* UTIL_H_ */
