/* button_resource.c - Handle bin empty acknowledgment */
#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "os/dev/button-hal.h"
#include <string.h>

static void res_post_handler(coap_message_t *request, coap_message_t *response,
                             uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

RESOURCE(res_button,
         "title=\"button event\";rt=\"Input\"",
         NULL,
         NULL,
         NULL,
         res_post_handler);

static void res_post_handler(coap_message_t *request, coap_message_t *response,
                             uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  leds_off(LEDS_RED);
  leds_on(LEDS_GREEN);
  coap_set_status_code(response, CHANGED_2_04);
  printf("Bin emptied acknowledged via button.\n");
}