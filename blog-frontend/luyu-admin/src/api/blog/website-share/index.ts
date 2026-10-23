export function websiteShareList() { return useGet('/website-share/back/list') }
export function getWebsiteShare(id: string | number) { return useGet(`/website-share/back/get/${id}`) }
export function addWebsiteShare(data: unknown) { return usePut('/website-share/back/add', data) }
export function updateWebsiteShare(data: unknown) { return usePost('/website-share/back/update', data) }
export function deleteWebsiteShares(ids: Array<string | number>) { return useDelete('/website-share/back/delete', JSON.stringify(ids)) }
