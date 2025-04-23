import { ref, computed, watch, type Ref, type ComputedRef } from 'vue';
import type { ReadableTocItem } from '@/pages/novel/components/common';

interface TocSection {
  separator: ReadableTocItem | null;
  chapters: ReadableTocItem[];
}

export function useTocExpansion(
  toc:
    | Ref<ReadableTocItem[] | undefined>
    | ComputedRef<ReadableTocItem[] | undefined>,
  sortReverse: Ref<boolean> | ComputedRef<boolean>,
  defaultExpanded: Ref<boolean> | ComputedRef<boolean>,
) {
  const expandedState = ref(new Map<string, boolean>());

  watch(
    [toc, defaultExpanded],
    ([newToc, newDefaultExpanded]) => {
      if (!newToc) return;
      let needsUpdate = false;
      const currentState = expandedState.value;
      const newState = new Map(currentState);

      for (const item of newToc) {
        if (item.order === undefined) {
          const key = item.titleJp;
          if (!currentState.has(key)) {
            newState.set(key, newDefaultExpanded ?? true);
            needsUpdate = true;
          }
        }
      }
      if (needsUpdate) {
        expandedState.value = newState;
      }
    },
    { immediate: true, deep: true },
  );

  const hasSeparators = computed(() => {
    return toc.value?.some((item) => item.order === undefined) ?? false;
  });

  const isAnyExpanded = computed(() => {
    if (!hasSeparators.value || !toc.value) {
      return false;
    }
    for (const item of toc.value) {
      if (item.order === undefined) {
        const key = item.titleJp;
        if (expandedState.value.get(key) ?? defaultExpanded.value) {
          return true;
        }
      }
    }
    return false;
  });

  const toggleAll = () => {
    if (!toc.value) return;
    const targetState = !isAnyExpanded.value;
    const newState = new Map(expandedState.value);
    for (const item of toc.value) {
      if (item.order === undefined) {
        newState.set(item.titleJp, targetState);
      }
    }
    expandedState.value = newState;
  };

  const toggleSection = (separatorKey: string) => {
    const currentState =
      expandedState.value.get(separatorKey) ?? defaultExpanded.value;
    const newState = new Map(expandedState.value);
    newState.set(separatorKey, !currentState);
    expandedState.value = newState;
  };

  const finalToc = computed(() => {
    if (!toc.value) {
      return [];
    }
    const sections: TocSection[] = [];
    let currentSection: TocSection = { separator: null, chapters: [] };

    for (const item of toc.value) {
      if (item.order === undefined) {
        if (currentSection.separator || currentSection.chapters.length > 0) {
          sections.push(currentSection);
        }
        currentSection = { separator: item, chapters: [] };
      } else {
        currentSection.chapters.push(item);
      }
    }
    sections.push(currentSection);

    const filteredSections = sections.map((section) => {
      if (section.separator) {
        const isExpanded =
          expandedState.value.get(section.separator.titleJp) ??
          defaultExpanded.value;
        return {
          ...section,
          chapters: isExpanded ? section.chapters : [],
        };
      }
      return section;
    });

    let result: ReadableTocItem[] = [];
    if (!sortReverse.value) {
      filteredSections.forEach((section) => {
        if (!section) return;
        if (section.separator) {
          result.push(section.separator);
        }
        result.push(...section.chapters);
      });
    } else {
      const reversedSections = filteredSections.slice().reverse();
      reversedSections.forEach((section) => {
        if (!section) return;
        if (section.separator) {
          result.push(section.separator);
        }
        result.push(...section.chapters.slice().reverse());
      });
    }

    return result;
  });

  return {
    expandedState,
    hasSeparators,
    isAnyExpanded,
    toggleAll,
    toggleSection,
    finalToc,
  };
}
