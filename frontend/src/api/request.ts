import axios, { type AxiosResponse, type AxiosRequestConfig } from 'axios'

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    // 业务失败（如放行被安全岗拦下）：把后端写明的原因透传出去
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const msg = error?.response?.data?.message
    if (msg) {
      return Promise.reject(new Error(msg))
    }
    return Promise.reject(error)
  }
)

const request = {
  get: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    instance.get(url, config) as Promise<T>,
  post: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
    instance.post(url, data, config) as Promise<T>,
  put: <T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> =>
    instance.put(url, data, config) as Promise<T>,
  delete: <T = any>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    instance.delete(url, config) as Promise<T>
}

export default request
