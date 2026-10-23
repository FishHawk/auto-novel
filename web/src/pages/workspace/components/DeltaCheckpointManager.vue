<script lang="ts" setup>
import { StorageOutlined } from '@vicons/material';
import { XDialog } from '@novelia/web-kit';
import { computed, ref } from 'vue';
import type { DeltaSessionSummary } from '@/domain/translator/delta/session-summary';

const props = defineProps<{
  sessions: DeltaSessionSummary[];
  disabled: boolean;
}>();
const emit = defineEmits<{
  discardChapter: [sessionId: string];
  discardNovel: [novelId: string];
  discardPending: [];
}>();
const show = ref(false);
const novels = computed(() => {
  const groups = new Map<string, DeltaSessionSummary[]>();
  for (const session of props.sessions) {
    const id = session.identity.novelId;
    const chapters = groups.get(id) ?? [];
    chapters.push(session);
    groups.set(id, chapters);
  }
  return [...groups].map(([id, chapters]) => {
    const completedCount = chapters.filter(
      (c) => c.phase === 'completed',
    ).length;
    const totalCount = chapters.length;
    return {
      id,
      title: chapters[0].metadata.title || id,
      chapters,
      completedCount,
      totalCount,
      isCompleted: completedCount === totalCount && totalCount > 0,
    };
  });
});
</script>

<template>
  <c-button
    label="数据管理"
    :icon="StorageOutlined"
    size="small"
    :round="false"
    :disabled="disabled || !sessions.length"
    @action="show = true"
  />
  <XDialog :open="show" title="Delta 数据管理" @update:open="show = $event">
    <n-empty
      v-if="!novels.length"
      description="暂无未完成的 Delta 数据"
      style="padding: 24px 0"
    />
    <template v-else>
      <n-flex
        justify="space-between"
        align="center"
        :wrap="false"
        style="margin-bottom: 12px; padding: 0 4px"
      >
        <n-text
          depth="3"
          style="
            min-width: 0;
            flex: 1;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-size: 13px;
          "
        >
          共 {{ novels.length }} 部小说 · {{ sessions.length }} 个章节
        </n-text>
        <n-popconfirm @positive-click="emit('discardPending')">
          <template #trigger>
            <n-button size="tiny" type="warning" secondary :disabled="disabled">
              清空任务缓存
            </n-button>
          </template>
          确认清空翻译缓存并清理未完成的翻译任务？
        </n-popconfirm>
      </n-flex>

      <n-collapse>
        <n-collapse-item
          v-for="novel in novels"
          :key="novel.id"
          :name="novel.id"
        >
          <template #header>
            <n-flex
              align="center"
              :size="8"
              :wrap="false"
              style="min-width: 0; flex: 1"
            >
              <span
                style="
                  display: -webkit-box;
                  min-width: 0;
                  flex: 1;
                  overflow: hidden;
                  text-overflow: ellipsis;
                  white-space: normal;
                  overflow-wrap: anywhere;
                  -webkit-box-orient: vertical;
                  -webkit-line-clamp: 2;
                "
              >
                {{ novel.title }}
              </span>
            </n-flex>
          </template>
          <template #header-extra>
            <n-flex align="center" :size="8" :wrap="false">
              <n-tag
                size="tiny"
                :bordered="false"
                round
                :type="novel.isCompleted ? 'success' : 'warning'"
              >
                {{ novel.completedCount }}/{{ novel.totalCount }}
              </n-tag>
              <n-popconfirm @positive-click="emit('discardNovel', novel.id)">
                <template #trigger>
                  <n-button
                    size="tiny"
                    type="error"
                    secondary
                    :disabled="disabled"
                    @click.stop
                  >
                    清空全书
                  </n-button>
                </template>
                确定要清空《{{ novel.title }}》的全部 Delta 数据吗？
              </n-popconfirm>
            </n-flex>
          </template>
          <n-list hoverable>
            <n-list-item v-for="session in novel.chapters" :key="session.id">
              <n-flex
                justify="space-between"
                align="center"
                style="width: 100%"
              >
                <div>
                  <span>
                    {{
                      session.metadata.chapterTitle ||
                      session.identity.title ||
                      session.identity.chapterId
                    }}
                  </span>
                  <n-text depth="3" style="font-size: 12px; margin-left: 8px">
                    ({{ session.cursor }}/{{ session.batchCount }} 批 ·
                    {{
                      session.pauseReason
                        ? '已暂停'
                        : session.phase === 'completed'
                          ? '已完成'
                          : '进行中'
                    }})
                  </n-text>
                </div>
                <n-popconfirm
                  @positive-click="emit('discardChapter', session.id)"
                >
                  <template #trigger>
                    <n-button
                      size="tiny"
                      type="warning"
                      secondary
                      :disabled="disabled"
                    >
                      清除
                    </n-button>
                  </template>
                  确定要清除该章节的 Delta 进度吗？
                </n-popconfirm>
              </n-flex>
            </n-list-item>
          </n-list>
        </n-collapse-item>
      </n-collapse>
    </template>
  </XDialog>
</template>

<style scoped>
:deep(.n-collapse-item__header-main) {
  min-width: 0;
}

:deep(.n-collapse-item__header-extra) {
  flex-shrink: 0;
}
</style>
