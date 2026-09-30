The two sample tables preserve the selected Phone (3a) stock lights HAL countdowns.

Source binary SHA256: `12d03e2fe9d4f9634afd956e5fcc2fa93b57d2f513734d1df600f54d2201ddda`.

| File | Stock config/data virtual address | Frames | SHA256 |
|---|---|---:|---|
| countdown-3s.bin | 0xdcb30 / 0xce6d0 | 182 | 24d626b4a96cb577388ec4c3f061a8d250a4ee7f6c18b1759270ba78d6117703 |
| countdown-10s.bin | 0xdcb48 / 0xd1a00 | 601 | 49b57cd24322a250615ccbb87ccb2f20d299dd8a3cb70e9649ed0c7a47ebbc5b |

Each frame contains 36 logical channels. Each unsigned little-endian stock 12-bit
sample was converted to raw8 with `floor(sample * 255 / 4095)`, matching the selected
shim's full-brightness request and Linux 6.1 AW20036 streaming driver. Playback uses
16,666 microseconds per frame, the driver's clock. The streaming and frame sysfs
interfaces use the same logical-to-register map. Terminal dark frames are retained.

`Lights_effect_thread` selects configs by seconds at config offset16 and calls
`set_csv_effect`; both function names are recoverable from the ELF mini debug data.
Commands15/43 start the respective tables;3/6/10/14/42 stop the Phone3a camera fill.
Ordinary fill commands7 and11 draw every channel; the stock code formats36 values
for parameter1 and parameter2. Recording105 uses raw register13 (logical channel15),
500ms on/500ms off, raw255 for BLACK and raw140 for factory GRAY.
