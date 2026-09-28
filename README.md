# Miao Library Cataloging

Android cataloguing application for Sub Divisional Library Miao (SDLM).

Workflow:
Physical evidence -> Vision extraction -> DDC matching -> AACR2 normalization -> MARC21 -> human review -> re-validation -> explicit confirmation -> Koha.

Rules:
- Koha testing API: http://92.4.70.3:8080/api/v1
- Default Koha MARC framework.
- 040 ## $c SDLM
- App does not generate Koha 001, 005 or 999.
- DDC target is edition 23; 082$2=23 only when verified.
- Item type defaults to BOOKS.
- Locations: CHILD, GEN, NALC, NE, RR, RRRLF.
- Acquisition sources: RRRLF, State Central Library, Donation.
- Languages: eng, hin, asm, ben, nep.
- Tezu, SCL Itanagar and Pasighat are DDC-only sources.
- Final 650 fields require validated LCSH.
- No Koha write before explicit final confirmation.
