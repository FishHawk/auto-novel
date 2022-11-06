import { ElMessage } from "element-plus";

export function handleError(error: any, prefix: string) {
  if (error.response) {
    ElMessage.error(
      `${prefix} ${error.response.status}:${error.response.data}`
    );
  } else if (error.request) {
    ElMessage.error(`${prefix} 没有收到回复`);
  } else {
    ElMessage.error(`${prefix} ${error.message}`);
  }
}