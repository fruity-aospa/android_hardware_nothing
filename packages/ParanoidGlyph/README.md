# Paranoid Glyph

Paranoid Glyph is the Settings app for devices with a Glyph LED interface. It
lets you turn the Glyph lights on or off, set their brightness, and configure
what each pattern does.

## What it supports

| Device | Codename | Soong config |
|---|---|---|
| Phone (1) | Spacewar | `phone1` |
| Phone (2) | Pong | `phone2` |
| Phone (3a), Phone (3a) Pro | asteroids | `phone3a` |

## Setup

To build the app, add it to `PRODUCT_PACKAGES` and set the Soong config for
your device from the table above. Example:
```make
# Paranoid Glyph
PRODUCT_PACKAGES += \
    ParanoidGlyph

$(call soong_config_set,nothing_glyph,device,phone1)
```
