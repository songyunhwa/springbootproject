<template>
  <div v-show="this.showModal">
    <transition name="modal">
      <div class="modal-mask">
        <div class="modal-wrapper">
          <div class="modal-container" style="height: max-content;">

            <div class="modal-header">
              <slot name="header">
                <h3>장소 추가하기</h3>
              </slot>
            </div>
            <div class="modal-body">
              <div>
                <div>장소이름</div>
                <div><input v-model="place.name" style="margin: 5px 10px;padding: 5px 10px;"
                            :disabled="isModify"></div>
              </div>
              <div>
                <div>지역</div>
                <div><input v-model="place.location" style="margin: 5px 10px;padding: 5px 10px;" :disabled="isModify"></div>
              </div>
              <div>
                <div>번호</div>
                <div><input v-model="place.number" style="margin: 5px 10px;padding: 5px 10px;"></div>
              </div>
              <div>
                <div>카테고리</div>
                <div>
                  <select name="category" v-model="place.subCategory" class="select-category"
                          :disabled="isModify">
                    <option v-for="(category, index) in categorys" v-bind:key="index"> {{
                        category.included
                      }}
                    </option>
                  </select>
                </div>
              </div>
              <div>
                <ul>
                  <li v-for="(youtube) in place.youtubes" v-bind:key="youtube">
                    {{ youtube.title }}
                  </li>
                </ul>
              </div>

              <div v-if="!addYoutube">
                <button @click="addYoutube?addYoutube=false:addYoutube=true;">유투브 추가</button>
              </div>
            </div>

            <div v-if="addYoutube">
              <div>
                <div>유투버 이름</div>
                <div><input v-model="youtube.channelTitle" style="margin: 5px 10px;padding: 5px 10px;">
                </div>
              </div>
              <div>
                <div>제목</div>
                <div><input v-model="youtube.title" style="margin: 5px 10px;padding: 5px 10px;"></div>
              </div>
              <div>
                <div>비디오 아이디</div>
                <div><input v-model="youtube.videoId" style="margin: 5px 10px;padding: 5px 10px;"></div>
              </div>
            </div>

            <div style="color:red; margin-top: 10px;">{{ result }}</div>
            <div class="modal-default-button">
              <button class="modal-default-button" @click="putYoutube" v-if="addYoutube">
                유투브 추가
              </button>
              <!--
              <button class="modal-default-button" @click="popPlaceYoutubes">
                  전체 유투브 삭제
              </button> -->
              <button @click="this.putPlace">
                확인
              </button>
              <button @click="this.$emit('close')">
                취소
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>
    <Modal v-show="showResultModal" :select_modal="modal" @close="onToggleResultModal"></Modal>

  </div>
</template>

<script>
import axios from "axios";
import Modal from "@/modal/Modal";

export default {
  name: 'PlaceModal',
  components: {Modal},
  props: {
    select_modal: Object,
  },
  data: () => ({
    place: {
      name: '',
     location: '', // 지역
     url: '',  // 유투브 페이지
      number: '', // 폰 번호
     subCategory:'', // CategoryModel 과 연결
      fileId: '', // 파일 아이디
       youtubes : [{
       videoId: '',
       channelTitle: '',
       title: '',
     }]
    },
    youtube: {
      videoId: '',
      channelTitle: '',
      title: '',
    },
    categorys: [{
      id: '',
      subCategory: '',
      included: '',
    }],
    addYoutube: false,
    showModal: false,
    showResultModal: false,
    isModify: false,
    modal: {
      header: '',
      body: '',
      footer: ''
    },
    url: '',
    result: '',
  }),
  created() {
    this.url = this.resourceHost;
    this.getCategory();
    this.popPlaceYoutubes();

    axios.defaults.withCredentials = true;
  },
  methods: {
    modifyYoutube(place) {

      this.addYoutube = false;
      this.isModify = true;
      this.place.name = place.name;
      this.place.number = place.number;
      this.place.location = '-'
      if(place.location[0] != null )
        this.place.location = place.location[0].address;


      if (place.subCategory != null) {
        let category = {
          id: '-1',
          subCategory: '',
          included: place.subCategory,
        };
        this.categorys.push(category);
        this.place.subCategory = place.subCategory;
      }

      // 유투브 집어넣기
      this.popPlaceYoutubes();
      if(place.youtube!=null) {
        for(let i=0; i< place.youtube.length ; i++) {
          this.place.youtubes.push(place.youtube[i]);
        }
      }


    },
    putYoutube() {
      if (this.youtube.videoId.length > 0 &&
          this.youtube.channelTitle.length > 0 &&
          this.youtube.title.length > 0) {

        let addYoutube = {
          videoId: this.youtube.videoId,
          channelTitle: this.youtube.channelTitle,
          title: this.youtube.title,
          url: this.youtube.url,
        };
        this.place.youtubes.push(addYoutube);

        this.youtube.videoId = '';
        this.youtube.channelTitle = '';
        this.youtube.title = '';
        this.addYoutube = false;
      } else {
        this.result = '유투브 정보를 채워주세요';
      }
    },
    putPlace() {
      if (this.place.name.length === 0) {
        this.result = '장소의 이름을 적어주세요.';
        return;
      }
      if (this.place.subCategory.length === 0) {
        this.result = '카테고리를 적어주세요.';
        return;
      }
      if (this.place.youtubes.length === 0) {
        this.result = '유투브가 하나 이상이어야 합니다.';
        return;
      }


      return axios
          .post(this.url + '/place', this.place)
          .then(() => {
            this.$emit('putPlace');
            this.$emit('close');
          })
          .catch((error) => {
            console.log(error);
            this.modal.body = error.response.data;
            this.onToggleResultModal();
          })


    },
    getCategory() {
      return axios
          .get(this.url + '/category')
          .then(({data}) => {
            this.categorys = data;
          })
          .catch((error) => {
            console.log(error);
          })
    },
    onToggleResultModal() {
      if (this.showResultModal) {
        this.showResultModal = false;
      } else {
        this.showResultModal = true;
      }
    },
    popPlaceYoutubes() {
      while (this.place.youtubes.length !== 0) {
        this.place.youtubes.pop();
      }
    }
  }
}
</script>
<style>
.select-category {
  width: 180px;
  height: 30px;
  padding-left: 5px;
  margin-left: 10px;
}

button:disabled {
  background: #ccc;
}
</style>
