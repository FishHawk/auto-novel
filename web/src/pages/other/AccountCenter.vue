<script lang="ts" setup>
import { Locator } from '@/data';
import { Setting } from '@/model/Setting';
import { UserRole } from '@/model/User';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';

const message = useMessage();

const { setting } = Locator.settingRepository();
const { userData, isSignedIn, atLeastAdmin, asAdmin, toggleAdminMode } =
  Locator.userDataRepository();

const roleToReadableText = (role: UserRole) => {
  if (role === 'normal') return '普通用户';
  else if (role === 'trusted') return '信任用户';
  else if (role === 'maintainer') return '维护者';
  else if (role === 'admin') return '管理员';
  else if (role === 'banned') return '封禁用户';
  else return '未知';
};

const clearWebSearchHistory = () => {
  Locator.webSearchHistoryRepository().clear();
  message.success('清空成功');
};

const clearWenkuSearchHistory = () => {
  Locator.wenkuSearchHistoryRepository().clear();
  message.success('清空成功');
};

const playSound = (source: string) => {
  return new Audio(source).play();
};
</script>

<template>
  <div class="layout-content">
    <template v-if="isSignedIn">
      <n-h1>
        @{{ userData.info?.username }}
        <n-tag :bordered="false" size="small" style="margin-left: 4px">
          {{ roleToReadableText(userData.info!!.role) }}
        </n-tag>
      </n-h1>

      <n-list bordered>
        <n-list-item>
          <n-flex vertical>
            <b>主题</b>
            <c-radio-group
              v-model:value="setting.theme"
              :options="Setting.themeOptions"
              size="small"
            />
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical>
            <b>快捷键说明</b>
            <n-ul>
              <n-li>列表页面，可以使用左右方向键翻页。</n-li>
              <n-li>
                GPT/Sakura排队按钮，按住Ctrl键点击，会将任务自动置顶。
              </n-li>
              <n-li>阅读页面，可以使用左右方向键跳转上/下一章。</n-li>
              <n-li>阅读页面，可以使用数字键1～4快速切换翻译。</n-li>
            </n-ul>
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical>
            <b>自定义UI</b>
            <n-flex vertical>
              <n-checkbox v-model:checked="setting.tocCollapseInNarrowScreen">
                移动端折叠网络小说目录
              </n-checkbox>
              <n-checkbox v-model:checked="setting.hideCommmentWebNovel">
                隐藏网络小说评论
              </n-checkbox>
              <n-checkbox v-model:checked="setting.hideCommmentWenkuNovel">
                隐藏文库小说评论
              </n-checkbox>
              <n-checkbox
                v-model:checked="setting.hideLocalVolumeListInWorkspace"
              >
                隐藏GPT/Sakura工作区本地小说列表
              </n-checkbox>
            </n-flex>
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical>
            <b>显示的翻译按钮</b>
            <translator-check
              v-model:value="setting.enabledTranslator"
              size="small"
            />
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical>
            <b>工作区语音提醒</b>
            <n-flex :wrap="false" :size="0">
              <n-checkbox v-model:checked="setting.workspaceSound">
                任务全部完成
              </n-checkbox>

              [<c-button
                label="点击播放"
                text
                type="primary"
                @action="playSound(SoundAllTaskCompleted)"
              />]
            </n-flex>
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical align="start">
            <b>清空搜索历史</b>
            <n-flex>
              <c-button
                label="清空网络搜索历史"
                size="small"
                @action="clearWebSearchHistory"
              />
              <c-button
                label="清空文库搜索历史"
                size="small"
                @action="clearWenkuSearchHistory"
              />
            </n-flex>
          </n-flex>
        </n-list-item>

        <n-list-item v-if="atLeastAdmin">
          <n-flex vertical>
            <b>控制台</b>
            <n-flex>
              <router-link to="/admin/user">
                <c-button label="控制台" size="small" />
              </router-link>
              <c-button
                :label="`管理员模式-${asAdmin}`"
                size="small"
                @action="toggleAdminMode()"
              />
            </n-flex>
          </n-flex>
        </n-list-item>
      </n-list>
    </template>
    <n-result v-else status="error" title="未登录" />
  </div>
</template>
