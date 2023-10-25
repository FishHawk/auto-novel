import { runCatching } from '@/data/result';

import { client } from './client';
import { Page, UserOutline } from './common';

export type OperationType =
  | 'web-edit'
  | 'web-edit-glossary'
  | 'wenku-edit'
  | 'wenku-edit-glossary'
  | 'wenku-upload';

type Operation =
  | OperationWebEdit
  | OperationWebEditGlossary
  | OperationWenkuEdit
  | OperationWenkuEditGlossary
  | OperationWenkuUpload;

interface OperationWebEditData {
  titleZh: string;
  introductionZh: string;
}

export interface OperationWebEdit {
  type: 'web-edit';
  providerId: string;
  novelId: string;
  old: OperationWebEditData;
  new: OperationWebEditData;
  toc: { jp: string; old?: string; new: string }[];
}

type Glossary = { [key: string]: string };

export interface OperationWebEditGlossary {
  type: 'web-edit-glossary';
  providerId: string;
  novelId: string;
  old: Glossary;
  new: Glossary;
}

interface OperationWenkuEditData {
  title: string;
  titleZh: string;
  authors: string[];
  artists: string[];
  introduction: string;
}

export interface OperationWenkuEdit {
  type: 'wenku-edit';
  novelId: string;
  old?: OperationWenkuEditData;
  new: OperationWenkuEditData;
}

export interface OperationWenkuEditGlossary {
  type: 'wenku-edit-glossary';
  novelId: string;
  old: Glossary;
  new: Glossary;
}

export interface OperationWenkuUpload {
  type: 'wenku-upload';
  novelId: string;
  volumeId: string;
}

export interface OperationHistory {
  id: string;
  operator: UserOutline;
  operation: Operation;
  createAt: number;
}

const listOperationHistory = (params: {
  page: number;
  pageSize: number;
  type: OperationType;
}) =>
  runCatching(
    client
      .get('operation-history', { searchParams: params })
      .json<Page<OperationHistory>>()
  );

const deleteOperationHistory = (id: string) =>
  runCatching(client.delete(`operation-history/${id}`).text());

// Toc merge

interface MergeHistoryData {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface MergeHistoryDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
  tocOld: MergeHistoryData[];
  tocNew: MergeHistoryData[];
}

const listMergeHistory = (page: number) =>
  runCatching(
    client
      .get('operation-history/toc-merge/', { searchParams: { page } })
      .json<Page<MergeHistoryDto>>()
  );

const deleteMergeHistory = (id: string) =>
  runCatching(client.delete(`operation-history/toc-merge/${id}`).text());

export const ApiOperation = {
  listOperationHistory,
  deleteOperationHistory,
  listMergeHistory,
  deleteMergeHistory,
};
