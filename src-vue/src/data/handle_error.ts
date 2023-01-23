export function errorToString(error: any) {
  if (error.response) {
    return `${error.response.status}:${error.response.statusText}`;
  } else if (error.request) {
    return `没有收到回复`;
  } else {
    return `${error.message}`;
  }
}
