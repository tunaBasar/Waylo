import { useRef, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { Image } from 'expo-image';
import { API_URL } from '@/config';
import { PhotoFallback } from '@/components/PhotoFallback';
import { MIN_TOUCH, space } from '@/theme/theme';
import type { Theme } from '@/theme/theme';
import { useStyles, useTheme } from '@/theme/ThemeContext';
import { photoAspect, photoUrl, type HotelPhoto } from './api';

function Slide({ hotelId, photo, width }: { hotelId: string; photo: HotelPhoto; width: number }) {
  const [failed, setFailed] = useState(false);
  const aspect = photoAspect(photo.width, photo.height);
  if (failed) return <PhotoFallback style={{ width, aspectRatio: aspect }} />;
  return (
    <Image
      source={{ uri: photoUrl(API_URL, hotelId, photo.id) }}
      accessibilityLabel={photo.caption ?? undefined}
      contentFit="cover"
      onError={() => setFailed(true)}
      style={{ width, aspectRatio: aspect }}
    />
  );
}

/** Paged photo gallery with counter chip and thumbnails. Renders nothing when there are no photos. */
export function HotelGallery({ hotelId, photos }: { hotelId: string; photos: HotelPhoto[] }) {
  const { color, type } = useTheme();
  const s = useStyles(makeStyles);
  const [width, setWidth] = useState(0);
  const [index, setIndex] = useState(0);
  const list = useRef<FlatList<HotelPhoto>>(null);
  if (photos.length === 0) return null;
  const goTo = (i: number) => {
    setIndex(i);
    list.current?.scrollToIndex({ index: i, animated: true });
  };
  return (
    <View style={{ gap: space.sm }}>
      <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} style={s.box}>
        {width > 0 ? (
          <FlatList
            ref={list}
            data={photos}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            keyExtractor={(p) => p.id}
            getItemLayout={(_, i) => ({ length: width, offset: width * i, index: i })}
            onMomentumScrollEnd={(e) => setIndex(Math.round(e.nativeEvent.contentOffset.x / width))}
            renderItem={({ item }) => <Slide hotelId={hotelId} photo={item} width={width} />}
          />
        ) : null}
        <View accessible accessibilityLabel={`Fotoğraf ${index + 1} / ${photos.length}`} style={s.counter}>
          <Text style={[type.monoSm, { color: color.surface }]}>{`${index + 1} / ${photos.length}`}</Text>
        </View>
      </View>
      {photos.length > 1 ? (
        <FlatList
          data={photos}
          horizontal
          showsHorizontalScrollIndicator={false}
          keyExtractor={(p) => p.id}
          contentContainerStyle={{ gap: space.sm }}
          renderItem={({ item, index: i }) => (
            <Pressable accessibilityRole="button" accessibilityLabel={`Fotoğraf ${i + 1} göster`} onPress={() => goTo(i)} style={[s.thumb, i === index && { borderColor: color.primary }]}>
              <Image source={{ uri: photoUrl(API_URL, hotelId, item.id) }} contentFit="cover" style={s.thumbImg} />
            </Pressable>
          )}
        />
      ) : null}
    </View>
  );
}

const makeStyles = ({ color, radius }: Theme) => StyleSheet.create({
  box: { borderRadius: radius.lg, overflow: 'hidden', backgroundColor: color.surfaceContainer },
  counter: { position: 'absolute', right: space.md, bottom: space.md, backgroundColor: color.onSurface, borderRadius: radius.full, paddingHorizontal: space.sm, paddingVertical: space.xs },
  thumb: { width: MIN_TOUCH + 20, height: MIN_TOUCH + 20, borderRadius: radius.md, overflow: 'hidden', borderWidth: 2, borderColor: 'transparent' },
  thumbImg: { width: '100%', height: '100%' },
});
