import axios from 'axios'
import router from "../router";

const request = axios.create({
    baseURL: '/api',
    timeout: 5000
})

// ==================== 请求拦截器 ====================
request.interceptors.request.use(config => {
    config.headers['Content-Type'] = 'application/json;charset=utf-8';

    // 从 sessionStorage 取出用户信息，提取 token 并设置 Authorization 头
    let userJson = sessionStorage.getItem("user")
    if (userJson) {
        try {
            let user = JSON.parse(userJson)
            if (user.token) {
                config.headers['Authorization'] = 'Bearer ' + user.token
            }
        } catch (e) {
            // JSON 解析失败，忽略
        }
    }

    return config
}, error => {
    return Promise.reject(error)
});

// ==================== 响应拦截器 ====================
request.interceptors.response.use(
    response => {
        let res = response.data;
        // 如果是返回的文件
        if (response.config.responseType === 'blob') {
            return res
        }
        // 兼容服务端返回的字符串数据
        if (typeof res === 'string') {
            res = res ? JSON.parse(res) : res
        }
        return res;
    },
    error => {
        console.log('err' + error) // for debug

        // 处理 401 未授权：清除本地存储并跳转登录页
        if (error.response) {
            const status = error.response.status;
            const requestUrl = error.config.url || '';

            // 401 或后端明确返回未登录 → 清除 token 并跳转
            // 注意：排除 /user/login 自身，避免密码错误时死循环
            if (status === 401 && requestUrl.indexOf('/user/login') === -1) {
                sessionStorage.removeItem("user");
                router.push("/login");
            }
        }

        return Promise.reject(error)
    }
)


export default request
