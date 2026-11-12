# RedAI

**RedAI** is an Android AI studio created by **Red aka LittleRedBigSmile**. This project is being brought together as one branded app, with a cleaner premium black-and-crimson interface and the RedAI assistant identity.

## Current development branch

This branch rebuilds the main Android screen and visual identity while retaining the existing native chat, local GGUF inference, saved conversations, privacy storage, character profiles, offline voice library, hands-free voice controls, web research, attachments, OCR and image studio.

## Current model setup

RedAI is delivered as one Android application. The 1.1 GB RedAI Q4_K_M GGUF is installed into the app's private storage through the in-app **INSTALL REDAI BRAIN** action; users do not need to locate or manually import the model file. The first-run installer asks for confirmation, recommends Wi-Fi, checks available storage, downloads the official release and SHA-256 sidecar, verifies the complete model before activation, and removes incomplete downloads. The model remains a separate large download rather than being embedded in the APK, keeping the app installer practical for Android distribution. Manual GGUF import remains available for compatible alternative models.

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
