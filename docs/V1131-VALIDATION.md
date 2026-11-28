# v1.13.1 stabilization

Baseline: 6634cde3cbf7551e66aa6b167b92af2658ba3ae7. Development branch only. Preserve manual interactions and native API usage. Standard regression and compiled API checks run in CI. UI deliberately changes, so identical pixels are not a success criterion for the changed disclosure view.

Test in game: Guild compost prep, mature normal/super/ultra, full/partial/empty bins, storage and repeated refill, harvest without planting, rebank, ordinary Guild run and departure; categorized priority items; seaweed to birdhouse banking; nested supply scroll and updates. The CJW calquat report remains unconfirmed; no speculative region changes. No new dependencies or automated game inputs. Bucket widget matching uses observed visible native item children rather than a guessed ID. Live client behavior is not established by synthetic tests.
