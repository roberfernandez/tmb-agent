// Keep pinch zoom inside the image so one-finger dragging pans the image.
export function imageGestures(viewport, image, getZoom, setZoom) {
  viewport.style.touchAction = 'none';
  image.draggable = false;
  let gesture;
  const center = touches => {
    const a = touches[0], b = touches[1] || a;
    const rect = viewport.getBoundingClientRect();
    return { x: (a.clientX + b.clientX) / 2 - rect.left,
      y: (a.clientY + b.clientY) / 2 - rect.top,
      distance: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY) };
  };
  function begin(touches) {
    if (!touches.length) { gesture = null; return; }
    const point = center(touches), zoom = getZoom();
    gesture = { count: touches.length, point, zoom,
      x: (viewport.scrollLeft + point.x) / zoom,
      y: (viewport.scrollTop + point.y) / zoom };
  }
  viewport.addEventListener('touchstart', e => begin(e.touches), { passive: true });
  viewport.addEventListener('touchmove', e => {
    if (!gesture || gesture.count !== e.touches.length) begin(e.touches);
    if (!gesture) return;
    e.preventDefault();
    const point = center(e.touches);
    if (e.touches.length > 1 && gesture.point.distance > 0) {
      setZoom(gesture.zoom * point.distance / gesture.point.distance);
    }
    viewport.scrollLeft = gesture.x * getZoom() - point.x;
    viewport.scrollTop = gesture.y * getZoom() - point.y;
  }, { passive: false });
  viewport.addEventListener('touchend', e => begin(e.touches), { passive: true });
  viewport.addEventListener('touchcancel', () => { gesture = null; }, { passive: true });
}
