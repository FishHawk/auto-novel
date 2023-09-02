<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { onMounted, ref } from 'vue';
import { CommentFilled } from '@vicons/material';

import ApiComment, { CommentDto, CommentPageDto } from '@/data/api/api_comment';
import { Ok, ResultState } from '@/data/api/result';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const props = defineProps<{
  postId: string;
}>();

interface Comment extends CommentDto {
  topElement?: any;
  page: number;
}

interface CommentPage extends CommentPageDto {
  page: number;
  items: Comment[];
}

const topElement = ref();
const commentPage = ref<ResultState<CommentPage>>();

onMounted(() => loadPage(1, true));

async function loadPage(page: number, isFirst: boolean = false) {
  const result = await ApiComment.list(props.postId, page - 1);
  if (result.ok) {
    commentPage.value = Ok({
      ...result.value,
      page,
      items: result.value.items.map((it) => ({ ...it, page: 1 })),
    });
    if (!isFirst) {
      topElement.value?.scrollIntoView({ behavior: 'smooth' });
    }
  } else {
    commentPage.value = result;
  }
}

async function loadSubPage(page: number, comment: Comment) {
  const result = await ApiComment.listSub(props.postId, comment.id, page - 1);
  if (result.ok) {
    comment.page = page;
    comment.pageNumber = result.value.pageNumber;
    comment.items = result.value.items;
    comment.topElement?.scrollIntoView({ behavior: 'smooth' });
  } else {
    message.error('回复加载错误：' + result.error.message);
  }
}

function refreshSubCommentsIfNeed(comment: Comment) {
  if (comment.page >= comment.pageNumber) {
    loadSubPage(comment.page, comment);
  }
}

const showInput = ref(false);
const replyContent = ref('');

function replyClicked() {
  const token = authInfoStore.token;
  if (token) {
    showInput.value = !showInput.value;
  } else {
    message.info('请先登录');
  }
}

async function reply() {
  const token = authInfoStore.token;
  if (token) {
    if (replyContent.value.length === 0) {
      message.info('回复内容不能为空');
    } else {
      const result = await ApiComment.reply(
        props.postId,
        undefined,
        undefined,
        replyContent.value
      );
      if (result.ok) {
        if (commentPage.value?.ok) {
          if (
            commentPage.value.value.page >= commentPage.value.value.pageNumber
          ) {
            loadPage(commentPage.value.value.page);
          }
        }
        replyContent.value = '';
        showInput.value = false;
      } else {
        message.error('回复加载错误：' + result.error.message);
      }
    }
  } else {
    message.info('请先登录');
  }
}
</script>

<template>
  <section>
    <div ref="topElement" />
    <SectionHeader title="评论">
      <n-button @click="replyClicked()">
        <template #icon>
          <n-icon :component="CommentFilled" />
        </template>
        发表评论
      </n-button>
    </SectionHeader>

    <template v-if="showInput">
      <n-input
        v-model:value="replyContent"
        :placeholder="`发表回复`"
        type="textarea"
        style="margin-top: 10px"
      />
      <n-button type="primary" @click="reply()" style="margin-top: 10px">
        发布
      </n-button>
      <n-divider />
    </template>

    <ResultView
      :result="commentPage"
      :showEmpty="(it: CommentPage) => it.items.length === 0"
      v-slot="{ value }"
    >
      <div
        v-for="comment in value.items"
        :ref="(el) => (comment.topElement = el)"
      >
        <Comment
          :postId="postId"
          :comment="comment"
          @replied="refreshSubCommentsIfNeed(comment)"
        />
        <div
          v-for="subComment in comment.items"
          style="margin-left: 30px; margin-top: 20px"
        >
          <Comment
            :postId="postId"
            :comment="subComment"
            :parentId="comment.id"
            @replied="refreshSubCommentsIfNeed(comment)"
          />
        </div>

        <n-pagination
          v-if="comment.pageNumber > 1"
          v-model:page="comment.page"
          :page-count="comment.pageNumber"
          :page-slot="7"
          style="margin-left: 30px; margin-top: 20px"
          @update:page="loadSubPage($event, comment)"
        />
        <n-divider />
      </div>

      <n-pagination
        v-if="value.pageNumber > 1"
        v-model:page="value.page"
        :page-count="value.pageNumber"
        :page-slot="7"
        style="margin-top: 20px"
        @update:page="loadPage($event)"
      />
    </ResultView>
  </section>
</template>
