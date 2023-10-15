import { api } from './api';
import { UserOutline } from './api_article';
import { WebNovelTocItemDto } from './api_web_novel';
import { Page } from './page';
import { Result, runCatching } from './result';

export type OperationType =
  | 'web-edit'
  | 'web-edit-glossary'
  | 'wenku-edit'
  | 'wenku-edit-glossary'
  | 'wenku-upload';
export type Operation =
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

export interface OperationHistory<T> {
  id: string;
  operator: UserOutline;
  operation: T;
  createAt: number;
}

type O = {
  (_: { page: number; pageSize: number; type: OperationType }): Promise<
    Result<Page<OperationHistory<Operation>>>
  >;
  (_: { page: number; pageSize: number; type: 'web-edit' }): Promise<
    Result<Page<OperationHistory<OperationWebEdit>>>
  >;
  (_: { page: number; pageSize: number; type: 'wenku-edit' }): Promise<
    Result<Page<OperationHistory<OperationWenkuEdit>>>
  >;
  (_: { page: number; pageSize: number; type: 'wenku-upload' }): Promise<
    Result<Page<OperationHistory<OperationWenkuUpload>>>
  >;
};

const listOperationHistory = (({
  page,
  pageSize,
  type,
}: {
  page: number;
  pageSize: number;
  type: OperationType;
}) =>
  runCatching(
    api
      .get('operation-history', { searchParams: { page, pageSize, type } })
      .json<Page<OperationHistory<Operation>>>()
  )) as any as O;

const deleteOperationHistory = (id: string) =>
  runCatching(api.delete(`operation-history/${id}`).text());

// Toc merge
export interface TocMergeHistoryDto {
  id: string;
  providerId: string;
  novelId: string;
  reason: string;
  tocOld: WebNovelTocItemDto[];
  tocNew: WebNovelTocItemDto[];
}

const listTocMergeHistory = (page: number) =>
  runCatching(
    api
      .get('operation-history/toc-merge/', { searchParams: { page } })
      .json<Page<TocMergeHistoryDto>>()
  );

const deleteMergeHistory = (id: string) =>
  runCatching(api.delete(`operation-history/toc-merge/${id}`).text());

export const ApiOperationHistory = {
  listOperationHistory,
  deleteOperationHistory,
  listTocMergeHistory,
  deleteMergeHistory,
};
