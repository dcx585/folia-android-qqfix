import path from 'path';
import { fileURLToPath } from 'url';
import { type Plugin, type UserConfig } from 'vite';
import react from '@vitejs/plugin-react';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * 无头构建用的虚拟模块。
 *
 * 上游这几个模块分别由 dev/pinyin、dev/i18n、dev/icuFolia 三个插件在构建期生成；
 * 在拿不到那些插件的环境里，这里给出同等形状的替身，保证模块本身一定能被解析。
 * 真正决定产物正确性的是下面 vite.config.ts 里那份 inspect 检查。
 */
const virtualModuleStub = (): Plugin => {
    const virtuals = new Set([
        'virtual:folia-locale-zh-TW',
        'virtual:folia-command-pinyin',
        'virtual:icu-folia-dict',
        'virtual:icu-folia-hmm',
    ]);
    return {
        name: 'folia-android-virtual-stub',
        enforce: 'pre',
        resolveId(id) {
            return virtuals.has(id) ? '\0' + id : null;
        },
        load(id) {
            switch (id) {
                case '\0virtual:folia-locale-zh-TW':
                    return "export { default } from '/src/i18n/locales/zh-CN';";
                case '\0virtual:folia-command-pinyin':
                    return 'export const PINYIN_BY_PHRASE = {};';
                case '\0virtual:icu-folia-dict':
                    return 'export const DICT_WORDS = "";\nexport const DICT_FREQS = "";\nexport const DICT_TOTAL = 0;';
                case '\0virtual:icu-folia-hmm':
                    return [
                        'export const HMM_START = { B: 0, E: 0, M: 0, S: 0 };',
                        'export const HMM_TRANS = { B: {}, E: {}, M: {}, S: {} };',
                        'export const HMM_EMIT = { B: ["", ""], E: ["", ""], M: ["", ""], S: ["", ""] };',
                        'export const T2S_PAIRS = "";',
                    ].join('\n');
                default:
                    return null;
            }
        },
    };
};

export default {
    root: __dirname,
    base: '/',
    resolve: {
        alias: { '@': path.resolve(__dirname, 'src') },
    },
    plugins: [virtualModuleStub(), react()],
    // 与上游 vite.config.ts 的 define 逐项对齐：缺任何一项都会在运行期抛 ReferenceError，
    // 表现就是启动页卡住（__DOCKER_STACK_VERSION__ 就是这样漏掉的）。
    define: {
        __COMMIT_HASH__: JSON.stringify('android-qqfix'),
        __GIT_BRANCH__: JSON.stringify('main'),
        __BUILD_REPO__: JSON.stringify('dcx585/folia-android-qqfix'),
        __BUILD_COMMIT__: JSON.stringify('android-qqfix'),
        __BUILD_KIND__: JSON.stringify('web'),
        __BUILD_HOST__: JSON.stringify(''),
        __APP_VERSION__: JSON.stringify('0.7.21'),
        __APP_VERSION_LABEL__: JSON.stringify('0.7.21-qqfix'),
        __APP_RELEASE_CHANNEL__: JSON.stringify('stable'),
        __DOCKER_STACK_VERSION__: JSON.stringify(''),
    },
    build: {
        outDir: path.resolve(__dirname, 'dist'),
        emptyOutDir: true,
        target: 'es2022',
        chunkSizeWarningLimit: 8000,
    },
} satisfies UserConfig;
