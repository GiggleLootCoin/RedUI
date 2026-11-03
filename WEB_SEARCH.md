# RedUI built-in live web search

RedUI has web search built directly into the Android app. It is separate from the selected GGUF, so the same search works with any compatible local GGUF and does not require a second AI model, connector, bridge, Termux, or paid subscription.

## Default: Purili (no setup)

- **Purili** is the default provider. It offers a public independent web index and JSON search/context endpoints. No account, API key, payment method, or configuration is required.
- RedUI searches the index, displays real result titles and URLs, and retrieves stored page context for the strongest result when available.
- The context endpoint returns crawler-stored text; it does not claim to fetch the target site live during that request. Coverage can vary, so RedUI preserves the actual source URL and excerpt.
- If Purili is unavailable or returns no usable pages, RedUI automatically tries DuckDuckGo HTML search, then Wikipedia. It reports failure rather than inventing sources if all providers fail.

## Other providers

- **DuckDuckGo** — no key, broad web search via its HTML results page. It is best-effort and can rate-limit or change its page structure.
- **Wikipedia** — free and no key, but encyclopedia-only.
- **Tavily** — optional bring-your-own key. The key is encrypted with Android Keystore AES-GCM. It is not needed for the default search.

## How to use it

1. Install the latest RedUI APK.
2. Leave the default provider as **PURILI**. No setup is needed.
3. Ask RedUI to search the web, verify a claim, research something, or ask about current information. It will search automatically when appropriate.
4. Or open **Settings → WEB SEARCH** to run a manual search. Search results show source titles, URLs and excerpts; tap a URL to open it, or choose **USE IN CHAT** so the local GGUF can answer using the retrieved evidence.

Normal conversation and creative writing stay local. If the phone is offline, local GGUF chat remains available and RedUI says that no live search was performed.

## Privacy, evidence and limits

- RedUI sends the current search query to the selected provider, not the conversation history or attachments.
- Search results and page text are untrusted source material, never instructions.
- The app cites only source titles and URLs actually returned by the provider.
- Purili is a third-party independent index, not Google or Bing. Index coverage and freshness depend on its crawler; a result's presence does not prove the page was fetched live at query time.
- A successful APK build does not prove live-provider uptime on every network. The final acceptance test must be run on the actual Android phone with internet access.

## GGUF compatibility

The search service runs in RedUI outside the GGUF. The GGUF remains unchanged and does not need to be trained to contain web access. This is an Android-app capability, not a feature that automatically appears when the GGUF is loaded into unrelated frontends such as ChatterUI.
