import EasyTyper from "easy-typer-js";
// 第三方的api接口
// 一言接口
let myYiYan = import.meta.env.VITE_YIYAN_API
if (!myYiYan) {
    myYiYan = 'https://v1.hitokoto.cn/?c=a&encode=json'
}
// 每日鸡汤
export const getSoup = () => {
    return fetch(myYiYan, { credentials: 'omit' }).then(res => {
        if (!res.ok) throw new Error('一言服务暂时不可用')
        return res.json()
    })
}

// 打字机-每日鸡汤
export const getSoupTyping = (obj: any) => {
    fetch(myYiYan)
        .then((res) => {
            return res.json();
        })
        .then(({hitokoto}) => {
            new EasyTyper(
                obj,
                hitokoto,
                () => {
                    getSoupTyping(obj)
                },
                () => {
                }
            );
        });
}
