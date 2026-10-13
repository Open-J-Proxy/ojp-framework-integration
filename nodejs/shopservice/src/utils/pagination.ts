export interface Page<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: {
      sorted: boolean;
      unsorted: boolean;
      empty: boolean;
    };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  totalElements: number;
  totalPages: number;
  last: boolean;
  numberOfElements: number;
  size: number;
  number: number;
  sort: {
    sorted: boolean;
    unsorted: boolean;
    empty: boolean;
  };
  first: boolean;
  empty: boolean;
}

export function toPageResponse<T>(
  items: T[],
  total: number = items.length,
  page: number = 0,
  size: number = 20
): Page<T> {
  const totalPages = Math.ceil(total / (size || 1)) || (total === 0 ? 0 : 1);
  return {
    content: items,
    pageable: {
      pageNumber: page,
      pageSize: size,
      sort: { sorted: false, unsorted: true, empty: true },
      offset: page * size,
      paged: true,
      unpaged: false,
    },
    totalElements: total,
    totalPages,
    last: page >= totalPages - 1,
    numberOfElements: items.length,
    size,
    number: page,
    sort: { sorted: false, unsorted: true, empty: true },
    first: page === 0,
    empty: items.length === 0,
  };
}
