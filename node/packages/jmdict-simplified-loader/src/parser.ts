import { Parser } from 'stream-json';

import {
  JMdictDictionaryMetadata,
  JMdictWord,
  JMnedictWord,
  Kanjidic2DictionaryMetadata,
  Kanjidic2Character,
} from '@scriptin/jmdict-simplified-types';

type DictionaryMetadata =
  | JMdictDictionaryMetadata
  | Kanjidic2DictionaryMetadata;

export type MetadataHandler = (metadata: DictionaryMetadata) => void;

export type EntryHandler<
  W extends JMdictWord | JMnedictWord | Kanjidic2Character,
> = (word: W) => void;

type Path = (string | number)[];

export function put(obj: object, path: Path, value: any) {
  if (path.length === 0) {
    throw new Error('Empty path is not allowed');
  }
  let current: object | Array<any> = obj;
  let last = path[path.length - 1];
  for (const [index, step] of path.entries()) {
    if (index === path.length - 1) break;
    if (typeof step === 'string') {
      current = (current as any)[step];
    } else {
      current = (current as Array<any>)[step];
    }
  }
  if (
    Array.isArray(current) &&
    typeof last === 'number' &&
    current.length === last
  ) {
    // lastKey is an index (number), but we always append
    current.push(value);
  } else if (typeof current === 'object' && typeof last === 'string') {
    (current as any)[last] = value;
  } else {
    throw new Error(
      `Invalid path: typeof(current)=${typeof current}, path=[${path}]`,
    );
  }
}

type DataChunk = {
  name: string;
  value?: string | number | boolean | null;
};

export function updatePathAfterValue(path: Path) {
  if (path.length === 0) return;
  const last = path[path.length - 1];
  if (typeof last === 'string') {
    // we're building an object and just received a new value for a pending key
    path.pop();
  } else {
    // we're building an array, and just received a new item
    path.pop();
    path.push(last + 1);
  }
}

export function parseMetadata(parser: Parser, handler: MetadataHandler) {
  const metadata: Partial<DictionaryMetadata> = {};
  const path: Path = [];
  parser.on('data', function parserDataHandler({ name, value }: DataChunk) {
    switch (name) {
      case 'startObject':
        if (path.length) put(metadata, path, {});
        return;
      case 'endObject':
        updatePathAfterValue(path);
        return;
      case 'keyValue':
        if (value === 'words' || value === 'characters') {
          // Array of words have started
          parser.off('data', parserDataHandler);
          handler(metadata as DictionaryMetadata);
        } else {
          path.push(value as string);
          put(metadata, path, undefined);
        }
        return;
      case 'startArray':
        put(metadata, path, []);
        path.push(0);
        return;
      case 'endArray':
        path.pop();
        updatePathAfterValue(path);
        return;
      case 'numberValue':
        put(metadata, path, Number.parseFloat(value as string));
        updatePathAfterValue(path);
        return;
      case 'stringValue':
      case 'trueValue':
      case 'falseValue':
      case 'nullValue':
        put(metadata, path, value);
        updatePathAfterValue(path);
        return;
    }
  });
}

export function parseEntries<
  W extends JMdictWord | JMnedictWord | Kanjidic2Character,
>(parser: Parser, handler: EntryHandler<W>) {
  let word: Partial<W> = {};
  const path: Path = [];
  let objectDepth = 0;
  parser.on('data', function parserDataHandler({ name, value }: DataChunk) {
    switch (name) {
      case 'startObject':
        objectDepth += 1;
        if (path.length) put(word, path, {});
        return;
      case 'endObject':
        objectDepth -= 1;
        updatePathAfterValue(path);
        if (objectDepth === 0) {
          handler(word as W);
          word = {};
        }
        return;
      case 'keyValue':
        path.push(value as string);
        put(word, path, undefined);
        return;
      case 'startArray':
        if (objectDepth === 0) {
          // Encountered the start of a words array
          return;
        }
        put(word, path, []);
        path.push(0);
        return;
      case 'endArray':
        if (objectDepth === 0) {
          // Encountered the end of a words array
          parser.off('data', parserDataHandler);
          return;
        }
        path.pop();
        updatePathAfterValue(path);
        return;
      case 'numberValue':
        put(word, path, Number.parseFloat(value as string));
        updatePathAfterValue(path);
        return;
      case 'stringValue':
      case 'trueValue':
      case 'falseValue':
      case 'nullValue':
        put(word, path, value);
        updatePathAfterValue(path);
        return;
    }
  });
}
