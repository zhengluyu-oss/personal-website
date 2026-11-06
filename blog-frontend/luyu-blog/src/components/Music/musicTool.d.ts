import type { MusicDescription, MusicId } from '@/apis/music'

export const MODELLIST: string[]
export const PLAYTYPE: { CUSTOM: string; TOP: string }
export function getNextMusic(len: number, index: number, playType: string, isPlayNext: boolean): number
export function addZero(time: number): number | string
export function calcMusicTime(time: number): string
export function calcMusicSchedule(current: number, duration: number): number
export function calcMusicCurrentTime(progress: number, duration: number): number
export function getMusicDetail(id: MusicId): Promise<{ url: string } | undefined>
export function getMusicDescription(id: MusicId): Promise<MusicDescription[] | undefined>
export function getLyric(id: MusicId): Promise<{ lyricList: string[]; lyricTimeList: number[] } | undefined>
