# Auction list A/B raw results

- A: baseline commit `c351b197527ae7c15a7c19fcd8cf3e1a901576e0`
- B: QueryDSL SQL `UNION ALL` candidate queries, JAR SHA-256 `91ae1ef1b0990f98d2afbf760174d1485f56a234a6f8bb9555e61413144d39b7`
- Environment and summarized results: [`docs/auction-list-query-round-trip-troubleshooting.md`](../../../docs/auction-list-query-round-trip-troubleshooting.md)
- Each `summary.json` file is emitted by k6's `--summary-export` option.
- `run1`~`run3` are the primary samples used for the three-run median.
- `late-control` files are the final A samples used only to check time-order drift.
