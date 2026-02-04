import type { ConfigPlugin } from '@expo/config-plugins';
export type VoskPluginProps = {
    models?: string[];
    iOSMicrophonePermission?: string;
};
declare const _default: ConfigPlugin<void | VoskPluginProps>;
export default _default;
