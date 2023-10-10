import { api } from './api';
import { UserOutline } from './api_article';
import { Page } from './page';
import { Result, runCatching } from './result';

export type OperationType = 'wenku-upload' | 'wenku-edit';
export type Operation = OperationWenkuUpload | OperationWenkuEdit;

export interface OperationWenkuUpload {
  type: 'wenku-upload';
  novelId: string;
  volumeId: string;
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
  (_: { page: number; pageSize: number; type: 'wenku-upload' }): Promise<
    Result<Page<OperationHistory<OperationWenkuUpload>>>
  >;
  (_: { page: number; pageSize: number; type: 'wenku-edit' }): Promise<
    Result<Page<OperationHistory<OperationWenkuEdit>>>
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

export const ApiOperationHistory = {
  listOperationHistory,
  deleteOperationHistory,
};
