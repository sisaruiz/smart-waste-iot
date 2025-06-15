/* button_resource.c – Handle bin empty acknowledgment as an event resource */

#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "os/dev/button-hal.h"
#include "sys/log.h"

#define LOG_MODULE "btn-res"
#define LOG_LEVEL  LOG_LEVEL_DBG

static void res_post_handler(coap_message_t *request, coap_message_t *response,
                             uint8_t *buffer, uint16_t preferred_size, int32_t *offset);
static void trigger_event(void);

/*
 * An event-driven CoAP resource to acknowledge bin empty via button
 * Resource attributes only – CoAP engine will assign URI based on name
 */
EVENT_RESOURCE(res_button,
               "title=\"button event\";rt=\"Input\"",
               NULL,               /* GET  */
               res_post_handler,   /* POST */
               NULL,               /* PUT  */
               NULL,               /* DELETE */
               trigger_event);     /* event handler */

static void
res_post_handler(coap_message_t *request, coap_message_t *response,
                 uint8_t *buffer, uint16_t preferred_size, int32_t *offset) {
  /* Toggle LEDs to indicate acknowledgment */
  leds_off(LEDS_RED);
  leds_on(LEDS_GREEN);

  LOG_INFO("Bin emptied acknowledged via button\n");

  /* Notify any observers that an event occurred */
  trigger_event();

  /* Respond with 2.04 Changed, no payload */
  coap_set_status_code(response, CHANGED_2_04);
}

static void
trigger_event(void) {
  /* Notify all clients observing this resource */
  coap_notify_observers(&res_button);
  LOG_DBG("Notified observers of button event\n");
}
