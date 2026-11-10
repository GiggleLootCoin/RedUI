# RedAI

**RedAI** is an Android AI studio created by **Red aka LittleRedBigSmile**. This project is being brought together as one branded app, with a cleaner premium black-and-crimson interface and the RedAI assistant identity.

## Current development branch

This branch rebuilds the main Android screen and visual identity while retaining the existing native chat, local GGUF inference, saved conversations, privacy storage, character profiles, offline voice library, hands-free voice controls, web research, attachments, OCR and image studio.

## Current model setup

The current underlying app build still imports a compatible GGUF model into private app storage. The single-install package with the selected RedAI model bundled inside is a separate integration step and is not claimed as complete by this UI redesign.

## Brand

- Product: **RedAI**
- Creator and developer: **Red aka LittleRedBigSmile**
- Interface: native Android
- Visual direction: premium black, deep charcoal and vivid crimson
- Privacy: local inference and local conversation storage; network features are optional and disclose their external providers

## Build and verification

The Android workflow builds an ARM64 APK and a universal test APK. A successful CI build does not replace physical-device testing. Galaxy A12 installation, model loading, inference speed, thermals, microphone behavior and bundled voice playback must be verified on-device before a public paid release.

## Licensing and release readiness

Before commercial distribution, verify the licenses and redistribution terms for every bundled model, voice, native library and third-party asset. Do not advertise the one-download built-in-model experience until the model is actually included and tested.
