# RedAI

**RedAI** is an Android AI studio created by **Red aka LittleRedBigSmile**. This project is being brought together as one branded app, with a cleaner premium black-and-crimson interface and the RedAI assistant identity.

## Current development branch

This branch rebuilds the main Android screen and visual identity while retaining the existing native chat, local GGUF inference, saved conversations, privacy storage, character profiles, offline voice library, hands-free voice controls, web research, attachments, OCR and image studio.

## Unified RedAI setup

RedAI is presented as one Android assistant with one managed private intelligence package. The interface does not expose GGUF terminology, alternate model choices, or a manual model-file picker. RedAI contains one fixed Qwen3-1.7B Q4_K_M brain inside the APK. The build downloads the pinned official GGUF, verifies its SHA-256 and GGUF file signature, splits it into memory-safe uncompressed APK assets, and packages every chunk. On first launch, the app reassembles the chunks into private storage and verifies the complete file before enabling chat. A previously installed copy is verified once and reused; later launches use a local verification marker instead of rehashing 1.1 GB every time. There is no model picker, model upload, alternate model support, separate model download, or runtime model-host dependency. Allow approximately 1.2 GB of free internal storage for the first-run private copy. The included offline voice collection currently provides five Piper voices, eight Kitten Mini voices and 24 VCTK speaker choices. Once installed, local inference works offline; optional web research is a separate network feature.


## Brand

- Product: **RedAI**
- Creator and developer: **Red aka LittleRedBigSmile**
- Interface: native Android
- Visual direction: premium black, deep charcoal and vivid crimson
- Privacy: local inference and local conversation storage; network features are optional and disclose their external providers

## Build and verification

The Android workflow builds and verifies an ARM64 APK for compatible Android devices (including ARM64 Galaxy A-series phones). A successful CI build does not replace physical-device testing. Galaxy A12 installation, model loading, inference speed, thermals, microphone behavior and bundled voice playback must be verified on-device before a public paid release.

## Licensing and release readiness

Before commercial distribution, continue verifying the licenses and redistribution terms for every bundled model, voice, native library and third-party asset. The bundled-brain package and CI checks are in place; physical Galaxy A12 testing is still required before calling the build device-certified.
