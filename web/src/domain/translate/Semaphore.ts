export class Semaphore {
  private tasks: Array<() => void> = [];
  private currentCount: number = 0;

  constructor(private maxConcurrency: number) {}

  acquire(signal?: AbortSignal): Promise<() => void> {
    return new Promise((resolve, reject) => {
      const task = () => {
        if (signal?.aborted) {
          reject(new Error('翻译任务已被取消'));
          return;
        }
        this.currentCount++;
        const release = () => {
          this.currentCount--;
          if (this.tasks.length > 0) {
            const nextTask = this.tasks.shift();
            if (nextTask) nextTask();
          }
        };
        resolve(release);
      };

      if (signal?.aborted) {
        reject(new Error('翻译任务已被取消'));
        return;
      }

      const wrappedTask = () => {
        task();
      };

      if (this.currentCount < this.maxConcurrency) {
        wrappedTask();
      } else {
        this.tasks.push(wrappedTask);
        if (signal) {
          const onAbort = () => {
            const index = this.tasks.indexOf(wrappedTask);
            if (index > -1) {
              this.tasks.splice(index, 1);
              reject(new Error('翻译任务已被取消'));
            }
          };
          signal.addEventListener('abort', onAbort, { once: true });
        }
      }
    });
  }
}

// 设置您的最大并发量
var MAX_CONCURRENCY = 1;
// 修改MAX_CONCURRENC 的函数
export function setMaxConcurrency(maxConcurrency: number) {
  MAX_CONCURRENCY = maxConcurrency;
}

// 单例信号量实例
export var globalSemaphore = new Semaphore(MAX_CONCURRENCY);
// 单例信号量实例的函数
export function setGlobalSemaphore() {
  globalSemaphore = new Semaphore(MAX_CONCURRENCY);
}
