/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2020 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include header
#define SHARED_SPACE __local

#ifdef GL
  #error CL Only
#endif

SHARED_START
shared int totalNum[12]; // number of faces with a given priority
shared int totalDistance[12]; // sum of distances to faces of a given priority

shared int totalMappedNum[18]; // number of faces with a given adjusted priority

shared int min10; // minimum distance to a face of priority 10
shared int dfs[0]; // packed face id and distance
SHARED_END

#define FACE_STRIDE 8

#include comp_common.glsl

#include common.glsl
#include priority_render.glsl

typedef struct {
  ivec4 v1;
  ivec4 v2;
  ivec4 v3;
  int prio;
  int dist;
  int prioAdj;
  int idx;
} facedata;

__kernel void main(SHARED_ARGS KERNEL_ARGS) {
  uint groupId = get_group_id(0);
  uint localId = get_local_id(0) * FACE_STRIDE;
  modelinfo minfo = ol[groupId];
  ivec4 pos = NEWVEC(ivec4)(minfo.x, minfo.y, minfo.z, 0);

  if (localId == 0) {
    SHARED(min10) = 1600;
    for (int i = 0; i < 12; ++i) {
      SHARED(totalNum)[i] = 0;
      SHARED(totalDistance)[i] = 0;
    }
    for (int i = 0; i < 18; ++i) {
      SHARED(totalMappedNum)[i] = 0;
    }
  }

  facedata faces[FACE_STRIDE];

  for(uint i = 0; i < FACE_STRIDE; i++) {
    get_face(PASS_GLOBALS localId + i, minfo, &faces[i].prio, &faces[i].dist, &faces[i].v1, &faces[i].v2, &faces[i].v3);
  }

  memoryBarrierShared();
  barrier();

  for(uint i = 0; i < FACE_STRIDE; i++) {
    add_face_prio_distance(PASS_GLOBALS PASS_SHARED localId + i, minfo, faces[i].v1, faces[i].v2, faces[i].v3, faces[i].prio, faces[i].dist, pos);
  }

  memoryBarrierShared();
  barrier();

  for(uint i = 0; i < FACE_STRIDE; i++) {
    faces[i].idx = map_face_priority(PASS_SHARED localId + i, minfo, faces[i].prio, faces[i].dist, &faces[i].prioAdj);
  }

  memoryBarrierShared();
  barrier();

  for(uint i = 0; i < FACE_STRIDE; i++) {
    insert_dfs(PASS_SHARED localId + i, minfo, faces[i].prioAdj, faces[i].dist, faces[i].idx);
  }

  memoryBarrierShared();
  barrier();

  for(uint i = 0; i < FACE_STRIDE; i++) {
    sort_and_insert(PASS_GLOBALS PASS_SHARED localId + i, minfo, faces[i].prioAdj, faces[i].dist, faces[i].v1, faces[i].v2, faces[i].v3);
  }
}
