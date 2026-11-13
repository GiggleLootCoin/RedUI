# RedAI

**RedAI** is an Android AI studio created by **Red aka LittleRedBigSmile**. This project is being brought together as one branded app, with a cleaner premium black-and-crimson interface and the RedAI assistant identity.

## Current development branch

This branch rebuilds the main Android screen and visual identity while retaining the existing native chat, local GGUF inference, saved conversations, privacy storage, character profiles, offline voice library, hands-free voice controls, web research, attachments, OCR and image studio.

## Unified RedAI setup

RedAI is presented as one Android assistant with one managed private intelligence package. The interface does not expose GGUF terminology, alternate model choices, or a manual model-file picker. On first launch, RedAI offers a single setup flow for its official Qwen3-1.7B Q4_K_M brain. The package is downloaded once into app-private storage, checked against the pinned SHA-256 digest, and verified again when the app starts. Existing unrelated model files are left untouched and are not offered as choices.

The APK and intelligence package remain separate downloads in this build. This keeps the Android installer smaller; it is not represented as a single-file APK containing the model. Wi-Fi and approximately 1.2 GB of free space are recommended for first-time setup. The model runs locally after installation; optional web search is a separate network feature.


## Brand

- Product: **RedAI**
- Creator and developer: **Red aka LittleRedBigSmile**
- Interface: native Android
- Visual direction: premium black, deep charcoal and vivid crimson
- Privacy: local inference and local conversation storage; network features are optional and disclose their external providers

## Build and verification

The Android workflow builds and verifies an ARM64 APK for compatible Android devices (including ARM64 Galaxy A-series phones). A successful CI build does not replace physical-device testing. Galaxy A12 installation, model loading, inference speed, thermals, microphone behavior and bundled voice playback must be verified on-device before a public paid release.

## Licensing and release readiness

Before commercial distribution, verify the licenses and redistribution terms for every bundled model, voice, native library and third-party asset. Do not advertise the one-download built-in-model experience until the model is actually included and tested.
