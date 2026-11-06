import { config } from "@/config/config";
// 转码
export const Base64 = {
    encode: function (v: string) {
        return window.btoa(window.encodeURIComponent(v));
    },
    decode: function (v: string) {
        return window.decodeURIComponent(window.atob(v));
    },
};

export const _setLocalItem = function (key: string, value: unknown) {
    try {
        if (key === "" || key === undefined) {
            return;
        }
        if (key) {
            if (value == 0) {
                localStorage.setItem(config.ENCRYPTION ? Base64.encode(key) : key, JSON.stringify(value));
                return;
            }
            if (value === null || value === undefined || value === "") {
                return "";
            }
            // 编码
            let enObj = JSON.stringify(value);
            if (config.ENCRYPTION) {
                localStorage.setItem(Base64.encode(key), Base64.encode(enObj));
            } else {
                localStorage.setItem(key, enObj);
            }
        }
    } catch (err) {
        console.error(err);
    }
};
export const _getLocalItem = function (key: string | null | undefined) {
    try {
        if (key === null || key === "" || key === undefined) {
            return "";
        }
        if (key) {
            let value = localStorage.getItem(config.ENCRYPTION ? Base64.encode(key) : key);
            if (value === null || value === undefined || value === "") {
                return "";
            } else {
                value = config.ENCRYPTION ? Base64.decode(value) : value;
                return JSON.parse(value);
            }
        }
    } catch (err) {
        console.error(err);
    }
};

export const _setSessionItem = function (key: string, value: unknown) {
    try {
        if (key === "" || key === undefined) {
            return;
        }
        if (key) {
            if (value == 0) {
                sessionStorage.setItem(config.ENCRYPTION ? Base64.encode(key) : key, JSON.stringify(value));
            }
            if (value === null || value === undefined || value === "") {
                return "";
            }
            // 编码
            let enObj = JSON.stringify(value);
            if (config.ENCRYPTION) {
                sessionStorage.setItem(Base64.encode(key), Base64.encode(enObj));
            } else {
                sessionStorage.setItem(key, enObj);
            }
        }
    } catch (e) {
        console.log(e);
        return sessionStorage.setItem(config.ENCRYPTION ? Base64.encode(key) : key, String(value));
    }
};
export const _getSessionItem = function (key: string | null | undefined) {
    if (key === null || key === "" || key === undefined) {
        return null;
    }
    try {
        if (key) {
            let value = sessionStorage.getItem(config.ENCRYPTION ? Base64.encode(key) : key);
            if (value === null || value === undefined || value === "") {
                return value;
            } else {
                value = config.ENCRYPTION ? Base64.decode(value) : value;
                return JSON.parse(value);
            }
        } else {
            return key;
        }
    } catch (e) {
        console.log(e);
        return sessionStorage.getItem(config.ENCRYPTION ? Base64.encode(key) : key);
    }
};
export const _removeLocalItem = function (key: string | null | undefined) {
    if (key === null || key === "" || key === undefined) {
        return;
    }
    if (key) {
        let enObj = config.ENCRYPTION ? Base64.encode(key) : key;
        localStorage.removeItem(enObj);
    }
};
// 清空session数据
export const _removeSessionItem = function (key: string | null | undefined) {
    if (key === null || key === "" || key === undefined) {
        return;
    }
    if (key) {
        let enObj = config.ENCRYPTION ? Base64.encode(key) : key;
        sessionStorage.removeItem(enObj);
    }
};

/*
 * 递归深拷贝
 */
export function deepClone<T>(source: T): T {
    if (source === null || typeof source !== "object") {
        throw new Error("error arguments: deepClone");
    }
    const targetObj: Record<string, unknown> | unknown[] = Array.isArray(source) ? [] : {};
    Object.keys(source).forEach((keys) => {
        const value = (source as Record<string, unknown>)[keys];
        if (value && typeof value === "object") {
            (targetObj as Record<string, unknown>)[keys] = deepClone(value);
        } else {
            (targetObj as Record<string, unknown>)[keys] = value;
        }
    });
    return targetObj as T;
}

export function isMobile() {
    // 手机端
    let reg =
        /(phone|pad|iPhone|iPod|ios|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i;
    return reg.test(navigator.userAgent);
}

export function randomFontColor() {
    return `rgb(${Math.round(Math.random() * 255)},${Math.round(Math.random() * 255)},${Math.round(
        Math.random() * 255
    )})`;
}

// 给一些数字转成k w
export function numberFormate(number: number | string) {
    if (!number) return 0;
    number = typeof number == "number" ? number : parseFloat(number);
    let res;
    if (number >= 10000) {
        res = (number / 10000).toFixed(1) + "w";
    } else if (number >= 1000) {
        res = (number / 1000).toFixed(1) + "k";
    } else {
        res = number;
    }
    return res;
}

/**
 * 根据时间欢迎
 * @param {*} nickName 昵称
 * @returns 欢迎语句
 */
export function getWelcomeSay(nickName: string) {
    let res;
    if (!nickName) {
        res = "欢迎来到陆屿";
        return res;
    }
    const now = new Date().getHours();
    if (now >= 0 && now < 5) {
        res = `夜深了：${nickName}，请注意休息！`;
    } else if (now >= 5 && now < 9) {
        res = `早上好：${nickName}，今天又是活力满满的一天！`;
    } else if (now >= 9 && now < 12) {
        res = `上午好：${nickName}，请合理安排时间摸鱼！`;
    } else if (now >= 12 && now < 14) {
        res = `中午好：${nickName}，现在正适合睡一觉`;
    } else if (now >= 14 && now < 18) {
        res = `下午好：${nickName}`;
    } else if (now >= 18 && now < 20) {
        res = `傍晚好：${nickName}，记得按时吃饭`;
    } else if (now >= 20 && now < 22) {
        res = `晚上好: ${nickName}`;
    } else if (now >= 22) {
        res = `晚上好: ${nickName}，记得按时碎觉哦`;
    }

    return res;
}

/*
 * @author: Zhang Yuming
 * @date: 2023-07-13 11:14:04
 * @params: func 防抖的函数
 * @description: 防抖
 */
export function debounce(fn: (...args: unknown[]) => void, delay: number) {
    let timer: ReturnType<typeof setTimeout> | null = null;
    return function (this: unknown, ...args: unknown[]) {
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(() => {
            fn.apply(this, args);
        }, delay);
    };
}

export function containHTML(text: string) {
    const reg = /<[^>]+>/g;

    const badJs = /script|alert|window|prompt|location|href|iframe|onload|onerror/g;
    return reg.test(text) && !badJs.test(text);
}

export function filterMessage(text: string) {
    return text.replace(/&gt;/g, ">").replace(/&lt;/g, "<");
}

// 返回时间 几天前。。。
export const returnTime = (time: string) => {
    if (!time) return;
    // eslint-disable-next-line
    time = time.replace(/\-/g, "/"); // 解决ios系统上格式化时间出现NAN的bug
    let times = new Date().getTime() - new Date(time).getTime();
    let res;
    if (times < 6e4) {
        res = Math.trunc(times / 1000) + "秒";
    } else if (times >= 6e4 && times < 3.6e6) {
        res = Math.trunc(times / 6e4) + "分钟";
    } else if (times >= 3.6e6 && times < 8.64e7) {
        res = Math.trunc(times / 3.6e6) + "小时";
    } else {
        res = Math.trunc(times / 8.64e7) + "天";
    }
    return res;
};

/**
 * 图片压缩
 * @param {*} file 图片
 * @param {*} size 文件压缩至大小 实际上可能压缩不到 有可能会更小
 */
export const imageConversion = (file: File, size = 800, quality = 1, maxTime = 3) => {
    return new Promise<Blob>((resolve) => {
        try {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = (e) => {
                const image = new Image();
                const source = e.target?.result;
                if (typeof source !== 'string') return;
                image.src = source;
                image.onload = () => {
                    const canvas = document.createElement("canvas");
                    const ctx = canvas.getContext("2d");
                    let width = image.width;
                    let height = image.height;
                    width = image.width;
                    height = image.height;
                    canvas.width = width;
                    canvas.height = height;
                    if (!ctx) return;
                    ctx.drawImage(image, 0, 0, width, height);
                    let dataURL = canvas.toDataURL(file.type, quality);
                    // 在有限的时间内压缩
                    while (maxTime && quality > 0.2) {
                        if (Math.round(dataURL.length) / 1024 > size) {
                            maxTime--;
                            quality -= 0.2;
                            dataURL = canvas.toDataURL(file.type, quality);
                        } else {
                            break;
                        }
                    }

                    const arr = dataURL.split(",");
                    const mime = file.type;
                    const bstr = atob(arr[1]);
                    let n = bstr.length;
                    const u8arr = new Uint8Array(n);
                    while (n--) {
                        u8arr[n] = bstr.charCodeAt(n);
                    }

                    resolve(
                        new Blob([u8arr], {
                            type: mime,
                        })
                    );
                };
            };
        } catch (err) {
            console.error(err);
        }
    });
};
