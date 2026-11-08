# RedUI

RedUI is a free Android interface for local GGUF chat, voice conversations, character profiles, private local chat history and memory, web research, file/image attachments, and image creation.

**Creator:** Red aka LittleRedBigSmile. RedUI and its RedAI assistant identity are separate from the underlying foundation model.

## Install

1. Download the **ARM64 APK** from [GitHub Releases](https://github.com/GiggleLootCoin/RedUI/releases/latest).
2. Install it on an Android ARM64 device (Android 10 or newer).
3. Download a compatible `.gguf` model, such as the [RedAI MiniCPM5-1B Q4_K_M model](https://github.com/GiggleLootCoin/RedAI/releases/download/redai-minicpm5-candidate/RedAI-MiniCPM5-1B-Q4_K_M.gguf).
4. In RedUI, import the GGUF file through the model picker. The model is stored in the app's private local storage.

The APK does not contain the GGUF brain; import the model separately so you can choose or replace models.

## Included features

- Local GGUF inference with streamed replies and memory-mapped model loading.
- RedAI system identity and the creator-supplied Red's Ways Of Thinking framework.
- Saved chats, delete controls, user profiles, character profiles and character-card import.
- Offline English voice library, including British voices, preview clips and speaker choices.
- Talk Live controls, Android speech recognition and hands-free conversation.
- Web search with provider/privacy controls. Web requests require an internet connection.
- File and image attachments, text extraction/OCR, and image studio.
- Red/black interface, custom wallpaper and accent colour.
- Performance Center with AUTO, FASTEST POSSIBLE, BEST PERFORMANCE, DEEP REASONING and manual profiles.

## Performance defaults

The standard best-performance target is temperature 0.70, top-p 0.90, top-k 40, repeat penalty 1.10, 2,048-token context, four CPU threads, batch 128 and memory mapping. RedUI checks available memory and Android thermal status before inference and reduces context/batch/threads when the phone needs protection. Model configuration changes trigger a reload so context and batch settings actually take effect.

**Sampling limitation:** the currently integrated Llamatik 1.10.1 public `LlamaBridge.updateGenerateParams` API does not expose min-p. The RedUI settings data model retains a 0.05 target, but this version of the native bridge cannot apply it. RedUI does not claim otherwise.

## Privacy notes

- GGUF inference and saved chat/memory data are handled locally by the app.
- Web search sends the search query to the selected external search provider.
- Image generation uses the external AI Horde volunteer network. Prompts and optional source images are sent to that service and processed by volunteer workers. Review the in-app disclosure before sending anything private.
- Android speech recognition and system TTS availability depend on the speech services installed on the device.

## Device verification

The release workflow builds, signs and checks the APK and confirms bundled voice assets are present. A successful CI build is not the same as a physical-device test. Galaxy A12 installation, inference speed, thermals and microphone behavior still need to be verified on the handset.
