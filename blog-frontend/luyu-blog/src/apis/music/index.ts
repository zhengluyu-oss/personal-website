import http from '@/utils/http.ts'

export type MusicId = string | number

export interface MusicTrack {
  id: MusicId
  name?: string
  al?: { picUrl?: string }
  ar?: Array<{ name: string }>
  active?: boolean
}

export interface MusicDescription {
  name: string
  al: { picUrl: string }
  ar: Array<{ name: string }>
}

interface MusicResponse {
  code: number
}

export const reqToplist = () => http.get<MusicResponse & { list: MusicTrack[] }>('/wapi/toplist/detail')

export const reqTopDetaliList = ({ id, limit, offset }: { id: MusicId; limit: number; offset: number }) =>
  http.get<MusicResponse & { songs: MusicTrack[] }>(`/wapi/playlist/track/all?id=${id}&limit=${limit}&offset=${offset}`)

export const reqMusicDetail = ({ id, level }: { id: MusicId; level: string }) =>
  http.get<MusicResponse & { data: Array<{ url: string }> }>(`/wapi/song/url/v1?id=${id}&level=${level}`)

export const reqMusicDescription = (id: MusicId) =>
  http.get<MusicResponse & { songs: MusicDescription[] }>(`/wapi//song/detail?ids=${id}`)

export const reqSearch = (keyWords: string) =>
  http.get<MusicResponse & { result?: Record<string, unknown> }>(`/wapi/search/suggest?keywords=${encodeURIComponent(keyWords)}`)

export const reqSearchSingerHot = ({ id, limit, offset }: { id: MusicId; limit: number; offset: number }) =>
  http.get<MusicResponse & { songs: MusicTrack[] }>(`/wapi/artist/top/song?id=${id}&offset=${offset}&limit=${limit}&order=hot`)

export const reqMusicLyricById = (id: MusicId) =>
  http.get<MusicResponse & { lrc: { lyric: string } }>(`/wapi/lyric?id=${id}`)
