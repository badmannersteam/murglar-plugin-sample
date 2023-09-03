# Murglar plugin sample

Murglar supports plugins, that add support for the new music services, cloud drives, FTPs and other audio content
sources.

This repo is a plugin example, consists of:

- [sample-core](sample-core) - core implementation of plugin for service "Sample"
- [sample-android](sample-android) - android module, that bundles [sample-core](sample-core) to the apk
- [client-cli](client-cli) - simple CLI client with desktop middlewares implementations for testing `Murglar`s

> **NOTE: It's not an actual real service/plugin, just the example code how to write plugins.**
>
> **It contains some nonexistent urls/rest apis/etc - also just for example.**

For implementing your own plugin first read the
[README of main project](https://github.com/badmannersteam/murglar-plugins) and then proceed with the guide
in [Russian](GUIDE_RU.MD) or [English](GUIDE_EN.MD).