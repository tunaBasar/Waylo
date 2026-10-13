'use client';

import { useState, useEffect, useRef } from 'react';

const VIDEOS = [
  '/videos/travel-1.mp4',
  '/videos/travel-2.mp4',
  '/videos/travel-3.mp4',
  '/videos/travel-4.mp4',
  '/videos/travel-5.mp4',
  '/videos/travel-6.mp4',
  '/videos/travel-7.mp4',
  '/videos/travel-8.mp4',
];

export default function VideoBackground() {
  const [currentVideo, setCurrentVideo] = useState(0);
  const [fading, setFading] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleEnded = () => {
      setFading(true);
      const nextIdx = (currentVideo + 1) % VIDEOS.length;
      setTimeout(() => {
        setCurrentVideo(nextIdx);
        setFading(false);
      }, 1200);
    };

    video.addEventListener('ended', handleEnded);
    return () => video.removeEventListener('ended', handleEnded);
  }, [currentVideo]);

  const nextIdx = (currentVideo + 1) % VIDEOS.length;

  return (
    <>
      {/* Current video */}
      <video
        ref={videoRef}
        key={`current-${currentVideo}`}
        src={VIDEOS[currentVideo]}
        autoPlay
        muted
        playsInline
        className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-[1200ms] ease-in-out ${
          fading ? 'opacity-0' : 'opacity-100'
        }`}
      />

      {/* Next video (preloaded, fades in) */}
      <video
        key={`next-${nextIdx}`}
        src={VIDEOS[nextIdx]}
        autoPlay={fading}
        muted
        playsInline
        preload="auto"
        className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-[1200ms] ease-in-out ${
          fading ? 'opacity-100' : 'opacity-0'
        }`}
      />

      {/* Dark overlay */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-[1px]" />
    </>
  );
}
