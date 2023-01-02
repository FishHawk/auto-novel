import { MessageApiInjection } from 'naive-ui/es/message/src/MessageProvider';

export function handleError(message: MessageApiInjection, error: any, prefix: string) {
  message.error(`${prefix} ${errorToString(error)}`);
}

export function errorToString(error: any) {
  if (error.response) {
    return `${error.response.status}:${error.response.statusText}`;
  } else if (error.request) {
    return `没有收到回复`;
  } else {
    return `${error.message}`;
  }
}
