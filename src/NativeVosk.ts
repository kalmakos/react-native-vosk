import type { TurboModule, CodegenTypes } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type VoskOptions = {
  /**
   * Set of phrases the recognizer will seek on which is the closest one from
   * the record, add `"[unk]"` to the set to recognize phrases striclty.
   */
  grammar?: string[];
  /**
   * Timeout in milliseconds to listen.
   */
  timeout?: number;
  /**
   * Audio source type (Android).
   * - VOICE_RECOGNITION: Default, optimized for speech recognition (no echo cancellation)
   * - VOICE_COMMUNICATION: For VoIP calls (with echo cancellation)
   * - MIC: Standard microphone
   * - DEFAULT: System default
   * @default 'VOICE_RECOGNITION'
   */
  audioSource?: 'VOICE_RECOGNITION' | 'VOICE_COMMUNICATION' | 'MIC' | 'DEFAULT';
  /**
   * Enable Acoustic Echo Canceler (AEC) if available on device.
   * Recommended when using TTS to prevent the microphone from picking up speaker output.
   * @default false
   */
  enableAEC?: boolean;
  /**
   * Enable Noise Suppressor (NS) if available on device.
   * @default false
   */
  enableNS?: boolean;
  /**
   * Enable Automatic Gain Control (AGC) if available on device.
   * @default false
   */
  enableAGC?: boolean;
  /**
   * Optional identifier for the recognition session.
   * Can be used for logging or tracking purposes.
   */
  id?: string;
};

export interface Spec extends TurboModule {
  loadModel: (path: string) => Promise<void>;
  unload: () => void;

  start: (options?: VoskOptions) => Promise<void>;
  stop: () => void;

  addListener: (eventType: string) => void;
  removeListeners: (count: number) => void;

  readonly onResult: CodegenTypes.EventEmitter<string>;
  readonly onPartialResult: CodegenTypes.EventEmitter<string>;
  readonly onFinalResult: CodegenTypes.EventEmitter<string>;
  readonly onError: CodegenTypes.EventEmitter<string>;
  readonly onTimeout: CodegenTypes.EventEmitter<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Vosk');
