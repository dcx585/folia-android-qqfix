// src/nativeSettings/index.ts
// The bridge between the Android-native settings screen and the web app's stores.
//
// The native screen is dumb on purpose: it asks for a schema, draws it, and reports changes.
// Everything it needs to know lives behind `window.__foliaNativeSettings`, which the native
// plugin reaches with evaluateJavascript - no Capacitor round trip per row, and nothing to
// keep in sync by hand.

import { registerPlugin } from '@capacitor/core';
import { applyNativeSetting, buildNativeSettingsSchema } from './registry';
import type { NativeSettingsSchema } from './registry';

interface FoliaSettingsPlugin {
    /** Draws the native settings screen. `schema` is what this file produced. */
    open(options: { schema: string; title?: string }): Promise<void>;
    /** Native reporting a change back; answered with whether the row was known. */
    apply(options: { id: string; value: string }): Promise<{ applied: boolean }>;
}

const FoliaSettings = registerPlugin<FoliaSettingsPlugin>('FoliaSettings', {
    web: {
        implement: async () => {
            throw new Error('FoliaSettings is only available on the Android build');
        },
    },
});

const isNativeAndroid = (): boolean => {
    if (typeof window === 'undefined') return false;
    const capacitor = (window as any).Capacitor;
    return Boolean(capacitor && capacitor.isNativePlatform?.() && capacitor.getPlatform?.() === 'android');
};

/**
 * Values arrive from native as strings; each registry entry's `write` decides what to do with
 * them. Booleans and numbers are converted here so the registry can stay typed to its own kind.
 */
const coerceValue = (id: string, raw: string): string | number | boolean => {
    const schema = buildNativeSettingsSchema();
    const row = schema.settings.find(setting => setting.id === id);
    if (!row) return raw;
    if (row.kind === 'toggle') return raw === 'true';
    if (row.kind === 'number') {
        const parsed = Number(raw);
        return Number.isFinite(parsed) ? parsed : 0;
    }
    return raw;
};

/** Installs the global the native side calls into. Safe to call more than once. */
export const installNativeSettingsBridge = (): void => {
    if (typeof window === 'undefined') return;
    const target = window as unknown as {
        __foliaNativeSettings?: {
            schema: () => string;
            set: (id: string, value: string) => boolean;
        };
    };
    if (target.__foliaNativeSettings) return;
    target.__foliaNativeSettings = {
        schema: () => JSON.stringify(buildNativeSettingsSchema()),
        set: (id, value) => applyNativeSetting(id, coerceValue(id, value)),
    };
};

export const canOpenNativeSettings = (): boolean => isNativeAndroid();

/** Opens the Android-native settings screen. Resolves false when not running on Android. */
export const openNativeSettings = async (title?: string): Promise<boolean> => {
    if (!isNativeAndroid()) return false;
    installNativeSettingsBridge();
    const schema: NativeSettingsSchema = buildNativeSettingsSchema();
    await FoliaSettings.open({ schema: JSON.stringify(schema), title });
    return true;
};

export { buildNativeSettingsSchema };
