import request from '@/config/axios'

export function save(data) {
  return request.post({
    url: '/templateManage/save',
    data: data,
    loading: true
  })
}
export function templateDelete(id) {
  return request.post({
    url: '/templateManage/delete/' + id
  })
}

export function deleteCategory(id) {
  return request.post({
    url: '/templateManage/deleteCategory/' + id
  })
}

export function showTemplateList(data) {
  return request.post({
    url: '/templateManage/templateList',
    data: data
  })
}

export function findOne(id) {
  return request.get({
    url: '/templateManage/findOne/' + id
  })
}

export function find(data) {
  return request.post({
    url: '/templateManage/find',
    data: data,
    loading: true
  })
}

export function findCategories(data) {
  return request.post({
    url: '/templateManage/findCategories',
    data: data,
    loading: true
  })
}

export function nameCheck(data) {
  return request.post({
    url: '/templateManage/nameCheck',
    data: data
  })
}
