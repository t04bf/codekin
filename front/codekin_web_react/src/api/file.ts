import axios from 'axios'
import { privateHttp } from './private'
import { readData, type ApiEnvelope } from './http'

/** 限制来自后端枚举，前端不单独维护大小或扩展名白名单。 */
export interface UploadTypePolicy {
  code: string
  maxBytes: number
  extensions: string[]
  contentTypes: string[]
  maxWidth: number
  maxHeight: number
  maxPixels: number
}

export interface UploadSignature {
  fileId: string
  uploadUrl: string
  method: 'POST'
  fields: Record<string, string>
  expiresAt: string
  confirmationExpiresAt: string
}

export interface ReadyFile {
  fileId: string
  size: number
  contentType: string
  typeCode: string
}

// 直传不携带应用 JWT、cookies 或统一业务响应拦截器，文件数据不经过应用网关。
const storageHttp = axios.create({ timeout: 120_000, withCredentials: false })

export const fileApi = {
  uploadTypes() {
    return privateHttp.get<ApiEnvelope<UploadTypePolicy[]>>('/api/files/upload-types').then(readData)
  },
  signature(typeCode: string, file: File) {
    return privateHttp.post<ApiEnvelope<UploadSignature>>('/api/files/upload/signature', {
      typeCode, fileName: file.name, size: file.size, contentType: file.type,
    }).then(readData)
  },
  async directUpload(signature: UploadSignature, file: File, onProgress: (percent: number) => void) {
    const form = new FormData()
    for (const [key, value] of Object.entries(signature.fields)) form.append(key, value)
    // S3 POST 约定 file 在最后；Content-Type 的 boundary 由浏览器生成。
    form.append('file', file)
    await storageHttp.post(signature.uploadUrl, form, {
      onUploadProgress: ({ loaded, total }) => onProgress(Math.min(100, Math.round(loaded * 100 / (total || file.size)))),
    })
  },
  complete(fileId: string) {
    return privateHttp.post<ApiEnvelope<ReadyFile>>(`/api/files/${fileId}/complete`).then(readData)
  },
}
