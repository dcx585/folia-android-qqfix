import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';
import { type UserConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// 上游用 dev/ 下的两个插件（繁简转换、拼音索引）在构建时生成这两个虚拟模块。
// 这里给出最小实现：简繁转换直接回落简体，拼音索引留空（命令面板仍能按中文/英文检索，
// 只是少了拼音首字母匹配）。两者都不涉及播放与在线音源逻辑。
const localeStub = (): Plugin => ({
    name: 'folia-locale-zh-tw-stub',
    resolveId(id) {
        if (id === 'virtual:folia-locale-zh-TW') return '\0virtual:folia-locale-zh-TW';
        return null;
    },
    load(id) {
        if (id !== '\0virtual:folia-locale-zh-TW') return null;
        // 直接转出 zh-CN 的默认导出对象，繁体用户看到的是简体文案（不影响任何功能）。
        return "export { default } from '/src/i18n/locales/zh-CN';";
    },
});

const pinyinStub = (): Plugin => ({
    name: 'folia-command-pinyin-stub',
    resolveId(id) {
        if (id === 'virtual:folia-command-pinyin') return '\0virtual:folia-command-pinyin';
        return null;
    },
    load(id) {
        if (id !== '\0virtual:folia-command-pinyin') return null;
        return 'export const PINYIN_BY_PHRASE = {};';
    },
});

// 歌词分词（icu-folia）的两个数据模块同样由 dev/ 下的插件生成。这里给一份空词典：
// 中文歌词仍能按字断行，只是少了词组级别的分词与简繁归一。
const icuStub = (): Plugin => ({
    name: 'folia-icu-stub',
    resolveId(id) {
        if (id.startsWith('virtual:icu-folia-')) return '\0' + id;
        return null;
    },
    load(id) {
        if (id === '\0virtual:icu-folia-dict') {
            return 'export const DICT_WORDS = "";\nexport const DICT_FREQS = "";\nexport const DICT_TOTAL = 0;';
        }
        if (id === '\0virtual:icu-folia-hmm') {
            return [
                'export const HMM_START = { B: 0, E: 0, M: 0, S: 0 };',
                'export const HMM_TRANS = { B: {}, E: {}, M: {}, S: {} };',
                'export const HMM_EMIT = { B: ["", ""], E: ["", ""], M: ["", ""], S: ["", ""] };',
                'export const T2S_PAIRS = "";',
            ].join('\n');
        }
        return null;
    },
});

export default {
    root: __dirname,
    base: '/',
    resolve: {
        alias: { '@': path.resolve(__dirname, 'src') },
    },
    plugins: [localeStub(), pinyinStub(), icuStub(), react()],
    build: {
        outDir: path.resolve(__dirname, 'dist'),
        emptyOutDir: true,
        target: 'es2022',
        chunkSizeWarningLimit: 8000,
    },
} satisfies UserConfig;
