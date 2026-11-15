// src/nativeSettings/registry.ts
// The settings the Android-native settings screen knows how to show.
//
// The native screen has no idea what a zustand store is: it asks this registry for a schema
// (sections + rows + current values), draws it with Android widgets, and hands every change
// back here. One row here = one row there, so a setting is added in one place and appears in
// both, without the two sides being able to drift apart.
//
// Anything not listed here stays in the web settings modal; the native screen links to it at
// the bottom, so nothing is reachable only through the native page.

import type { AppLanguagePreference } from '../i18n/config';
import { useAudioSettingsStore } from '../stores/useAudioSettingsStore';
import { useLatticePerformanceModeStore } from '../stores/useLatticePerformanceModeStore';
import { useMotionSettingsStore } from '../stores/useMotionSettingsStore';
import { useSettingsModalStore } from '../stores/useSettingsModalStore';
import { useThemeSettingsStore } from '../stores/useThemeSettingsStore';
import {
    VISUALIZER_FRAME_RATE_STORAGE_KEY,
    parseVisualizerFrameRate,
    setGlobalVisualizerFrameRate,
} from '../utils/frameRateLimiter';
import type { VisualizerFrameRate } from '../types';

export type NativeSettingKind = 'toggle' | 'select' | 'number' | 'action';

export type NativeSettingOption = { value: string; label: string };

export type NativeSettingDef = {
    id: string;
    section: string;
    kind: NativeSettingKind;
    label: string;
    hint?: string;
    options?: NativeSettingOption[];
    unit?: string;
    min?: number;
    max?: number;
    step?: number;
    read: () => string | number | boolean;
    write: (value: string | number | boolean) => void;
};

export type NativeSectionDef = { id: string; title: string };

export type NativeSettingsSchema = {
    sections: NativeSectionDef[];
    settings: Array<Omit<NativeSettingDef, 'read' | 'write'> & {
        value: string | number | boolean;
    }>;
};

const SECTIONS: NativeSectionDef[] = [
    { id: 'general', title: '通用' },
    { id: 'playback', title: '播放' },
    { id: 'appearance', title: '外观' },
    { id: 'performance', title: '性能' },
];


const SETTINGS: NativeSettingDef[] = [
    {
        id: 'general.language',
        section: 'general',
        kind: 'select',
        label: '界面语言',
        options: [
            { value: 'system', label: '跟随系统' },
            { value: 'zh-CN', label: '简体中文' },
            { value: 'zh-TW', label: '繁體中文' },
            { value: 'en', label: 'English' },
            { value: 'in', label: 'Bahasa Indonesia' },
        ],
        read: () => useSettingsModalStore.getState().appLanguagePreference,
        write: (value) => {
            void useSettingsModalStore.getState().handleSetAppLanguagePreference(value as AppLanguagePreference);
        },
    },
    {
        id: 'general.autoplayOnLaunch',
        section: 'general',
        kind: 'toggle',
        label: '启动时自动播放',
        read: () => useAudioSettingsStore.getState().autoPlayOnLaunch,
        write: (value) => useAudioSettingsStore.getState().handleToggleAutoPlayOnLaunch(Boolean(value)),
    },
    {
        id: 'playback.fade',
        section: 'playback',
        kind: 'toggle',
        label: '暂停 / 继续淡入淡出',
        hint: '关掉后暂停与继续是硬切',
        read: () => useAudioSettingsStore.getState().playbackFadeEnabled,
        write: (value) => useAudioSettingsStore.getState().handleTogglePlaybackFade(Boolean(value)),
    },
    {
        id: 'playback.autoSkipUnplayable',
        section: 'playback',
        kind: 'toggle',
        label: '无法播放时自动跳过',
        hint: '受版权限制或加载失败的歌会倒数后跳过',
        read: () => useAudioSettingsStore.getState().autoSkipUnplayable,
        write: (value) => useAudioSettingsStore.getState().handleToggleAutoSkipUnplayable(Boolean(value)),
    },
    {
        id: 'playback.transcodeFallback',
        section: 'playback',
        kind: 'toggle',
        label: '音源失败时转码兜底',
        read: () => useAudioSettingsStore.getState().enableTranscodeFallback,
        write: (value) => useAudioSettingsStore.getState().handleToggleTranscodeFallback(Boolean(value)),
    },
    {
        id: 'playback.neteaseScrobble',
        section: 'playback',
        kind: 'toggle',
        label: '网易云播放记录同步',
        hint: '会上报到你的网易云账号',
        read: () => useAudioSettingsStore.getState().neteaseScrobbleEnabled,
        write: (value) => useAudioSettingsStore.getState().handleToggleNeteaseScrobble(Boolean(value)),
    },
    {
        id: 'playback.queueAdd',
        section: 'playback',
        kind: 'select',
        label: '添加歌曲时',
        options: [
            { value: 'append', label: '加到队列末尾' },
            { value: 'next', label: '插到下一首' },
        ],
        read: () => useAudioSettingsStore.getState().queueAddBehavior,
        write: (value) => useAudioSettingsStore.getState().handleSetQueueAddBehavior(value as 'append' | 'next'),
    },
    {
        id: 'playback.audioQuality',
        section: 'playback',
        kind: 'select',
        label: '默认音质',
        options: [
            { value: 'standard', label: '标准' },
            { value: 'high', label: '较高' },
            { value: 'ultra', label: '极高' },
            { value: 'lossless', label: '无损' },
            { value: 'hires', label: 'Hi-Res' },
        ],
        read: () => useAudioSettingsStore.getState().audioQuality,
        write: (value) => useAudioSettingsStore.getState().setAudioQuality(value as never),
    },
    {
        id: 'appearance.staticMode',
        section: 'appearance',
        kind: 'toggle',
        label: '静态封面模式',
        hint: '封面不随播放律动，省电',
        read: () => useThemeSettingsStore.getState().staticMode,
        write: (value) => useThemeSettingsStore.getState().handleToggleStaticMode(Boolean(value)),
    },
    {
        id: 'appearance.coverColorBg',
        section: 'appearance',
        kind: 'toggle',
        label: '封面取色做背景',
        read: () => useThemeSettingsStore.getState().useCoverColorBg,
        write: (value) => useThemeSettingsStore.getState().handleToggleCoverColorBg(Boolean(value)),
    },
    {
        id: 'appearance.disableHomeDynamicBackground',
        section: 'appearance',
        kind: 'toggle',
        label: '关闭首页动态背景',
        hint: '关掉后首页不再实时绘制动态底图',
        read: () => useThemeSettingsStore.getState().disableHomeDynamicBackground,
        write: (value) => useThemeSettingsStore.getState().handleToggleDisableHomeDynamicBackground(Boolean(value)),
    },
    {
        id: 'appearance.daylight',
        section: 'appearance',
        kind: 'toggle',
        label: '浅色界面',
        read: () => useThemeSettingsStore.getState().isDaylight,
        write: (value) => useThemeSettingsStore.getState().setDaylightPreference(Boolean(value)),
    },
    {
        id: 'performance.latticePerformanceMode',
        section: 'performance',
        kind: 'toggle',
        label: '首页墙性能优先',
        hint: '关掉后墙上的效果更全，也更吃性能',
        read: () => useLatticePerformanceModeStore.getState().enabled,
        write: (value) => useLatticePerformanceModeStore.getState().setEnabled(Boolean(value)),
    },
    {
        id: 'performance.followSystemReducedMotion',
        section: 'performance',
        kind: 'toggle',
        label: '跟随系统「减弱动态效果」',
        read: () => useMotionSettingsStore.getState().followSystemReducedMotion,
        write: (value) => useMotionSettingsStore.getState().handleToggleFollowSystemReducedMotion(Boolean(value)),
    },
    {
        id: 'performance.visualizerFrameRate',
        section: 'performance',
        kind: 'select',
        label: '可视化帧率上限',
        hint: '限帧能明显省电，60 以上在多数设备上看不出差别',
        options: [
            { value: 'off', label: '不限' },
            { value: '60', label: '60 FPS' },
            { value: '90', label: '90 FPS' },
            { value: '120', label: '120 FPS' },
        ],
        read: () => String(parseVisualizerFrameRate(localStorage.getItem(VISUALIZER_FRAME_RATE_STORAGE_KEY))),
        write: (value) => {
            localStorage.setItem(VISUALIZER_FRAME_RATE_STORAGE_KEY, String(value));
            setGlobalVisualizerFrameRate(parseVisualizerFrameRate(String(value)) as VisualizerFrameRate);
        },
    },
];

/** Everything the native screen draws, with the values the stores hold right now. */
export const buildNativeSettingsSchema = (): NativeSettingsSchema => ({
    sections: SECTIONS,
    settings: SETTINGS.map(({ read, write, ...def }) => ({ ...def, value: read() })),
});

/**
 * Applies one change from the native screen. Unknown ids are ignored rather than thrown: a
 * native screen left open across an app update can still name a row that no longer exists.
 */
export const applyNativeSetting = (id: string, value: string | number | boolean): boolean => {
    const def = SETTINGS.find(setting => setting.id === id);
    if (!def) return false;
    def.write(value);
    return true;
};

export const NATIVE_SETTING_IDS = SETTINGS.map(setting => setting.id);
