"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("@expo/config-plugins");
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const package_json_1 = __importDefault(require("../../package.json"));
const withVosk = (config, props) => {
    const { models = [], iOSMicrophonePermission } = props ?? {};
    // iOS: add microphone permission string
    if (iOSMicrophonePermission) {
        (0, config_plugins_1.withInfoPlist)(config, (configMod) => {
            configMod.modResults.NSMicrophoneUsageDescription =
                iOSMicrophonePermission;
            return configMod;
        });
    }
    // iOS: add model folders to Xcode project resources
    if (models.length) {
        (0, config_plugins_1.withXcodeProject)(config, (configMod) => {
            const project = configMod.modResults;
            const iosRoot = configMod.modRequest.platformProjectRoot; // <app>/ios
            config_plugins_1.IOSConfig.XcodeUtils.ensureGroupRecursively(project, 'Resources');
            models.forEach((relModelPath) => {
                const absSource = path_1.default.join(configMod.modRequest.projectRoot, relModelPath);
                if (!fs_1.default.existsSync(absSource)) {
                    console.warn('[react-native-vosk] iOS model path not found: ' + absSource);
                    return;
                }
                config_plugins_1.IOSConfig.XcodeUtils.addResourceFileToGroup({
                    filepath: path_1.default.relative(iosRoot, absSource),
                    groupName: 'Resources',
                    project,
                    isBuildFile: true,
                    verbose: true,
                });
            });
            return configMod;
        });
    }
    // Android: pass model paths via gradle properties so the library build.gradle can pick them up.
    if (models.length) {
        (0, config_plugins_1.withGradleProperties)(config, (configMod) => {
            const key = 'Vosk_models';
            const value = models.join(',');
            const existingIndex = configMod.modResults.findIndex((p) => p.type === 'property' && p.key === key);
            if (existingIndex >= 0) {
                const item = configMod.modResults[existingIndex];
                item.value = value;
            }
            else {
                configMod.modResults.push({ type: 'property', key, value });
            }
            return configMod;
        });
    }
    return config;
};
exports.default = (0, config_plugins_1.createRunOncePlugin)(withVosk, package_json_1.default.name, package_json_1.default.version);
